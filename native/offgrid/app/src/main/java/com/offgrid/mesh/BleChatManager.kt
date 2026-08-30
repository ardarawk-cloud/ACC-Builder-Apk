package com.offgrid.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.ArrayDeque
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@SuppressLint("MissingPermission")
class BleChatManager(
    private val context: Context,
    private val localDeviceId: String,
    private val onState: (String) -> Unit,
    private val onSecurePeer: (SecurePeer) -> Unit,
    private val onIncomingMessage: (String, String) -> Unit,
    private val onDelivered: (String) -> Unit
) {
    data class SecurePeer(
        val deviceId: String,
        val identityFingerprint: String,
        val safetyCode: String,
        val address: String
    )

    private val bluetoothManager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
    private val secureRandom = SecureRandom()
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val identityKeyPair: KeyPair = loadOrCreateIdentityKeyPair()

    private var gattServer: BluetoothGattServer? = null
    private var serverCharacteristic: BluetoothGattCharacteristic? = null
    private var clientGatt: BluetoothGatt? = null
    private var clientCharacteristic: BluetoothGattCharacteristic? = null
    private var activeRemote: BluetoothDevice? = null
    private var role: Role = Role.NONE

    private var localEphemeralKeyPair: KeyPair? = null
    private var sessionKey: SecretKey? = null
    private var remoteDeviceId: String? = null
    private var helloSent = false

    private var reconnectTarget: BluetoothDevice? = null
    private var reconnectEnabled = false
    private var reconnectAttempt = 0

    private val clientQueue = ArrayDeque<ByteArray>()
    private var clientWriteInFlight = false
    private val serverQueue = ArrayDeque<ByteArray>()
    private var serverNotifyInFlight = false
    private var frameCounter = 1
    private val assemblers = mutableMapOf<String, FrameAssembly>()

    fun startServer(): Boolean {
        if (gattServer != null) return true
        val server = bluetoothManager.openGattServer(context, serverCallback) ?: return false
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            DATA_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(cccd)
        service.addCharacteristic(characteristic)
        server.addService(service)
        gattServer = server
        serverCharacteristic = characteristic
        return true
    }

    fun connect(device: BluetoothDevice) {
        disconnect()
        reconnectTarget = device
        reconnectEnabled = true
        reconnectAttempt = 0
        openClient(device, false)
    }

    fun disconnect() {
        reconnectEnabled = false
        reconnectTarget = null
        reconnectAttempt = 0
        reconnectHandler.removeCallbacksAndMessages(null)
        runCatching { clientGatt?.disconnect() }
        runCatching { clientGatt?.close() }
        clientGatt = null
        clientCharacteristic = null
        if (role == Role.SERVER) {
            activeRemote?.let { runCatching { gattServer?.cancelConnection(it) } }
        }
        activeRemote = null
        role = Role.NONE
        clearTransportQueues()
        resetSessionCrypto()
    }

    fun close() {
        disconnect()
        runCatching { gattServer?.close() }
        gattServer = null
        serverCharacteristic = null
    }

    fun isSecure(): Boolean = sessionKey != null

    fun localIdentityFingerprint(): String = shortFingerprint(identityFingerprint(identityKeyPair.public.encoded))

    fun sendText(text: String): String? {
        val key = sessionKey ?: return null
        if (text.isBlank()) return null
        val id = UUID.randomUUID().toString()
        val encrypted = encrypt(key, text.toByteArray(Charsets.UTF_8))
        val frame = "M|$id|${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
        sendFrame(frame)
        return id
    }

    private fun openClient(device: BluetoothDevice, retry: Boolean) {
        role = Role.CLIENT
        activeRemote = device
        clearTransportQueues()
        resetSessionCrypto()
        onState(
            if (retry) "Reconnect attempt $reconnectAttempt/$MAX_RECONNECT_ATTEMPTS…"
            else "Connecting to ${safeAddress(device)}…"
        )
        clientGatt = device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun clearTransportQueues() {
        clientQueue.clear()
        serverQueue.clear()
        clientWriteInFlight = false
        serverNotifyInFlight = false
        assemblers.clear()
    }

    private fun resetSessionCrypto() {
        localEphemeralKeyPair = generateEcKeyPair()
        sessionKey = null
        remoteDeviceId = null
        helloSent = false
    }

    private fun sendHello() {
        if (helloSent) return
        val ephemeral = localEphemeralKeyPair ?: generateEcKeyPair().also { localEphemeralKeyPair = it }
        val ephemeralPub = Base64.encodeToString(ephemeral.public.encoded, Base64.NO_WRAP)
        val identityPub = Base64.encodeToString(identityKeyPair.public.encoded, Base64.NO_WRAP)
        val body = helloBody(localDeviceId, ephemeralPub, identityPub)
        val signature = signIdentity(body.toByteArray(Charsets.UTF_8))
        helloSent = true
        sendFrame("H2|$localDeviceId|$ephemeralPub|$identityPub|${Base64.encodeToString(signature, Base64.NO_WRAP)}")
    }

    private fun handleFrame(frame: String) {
        when {
            frame.startsWith("H2|") -> handleSignedHello(frame)
            frame.startsWith("H|") -> onState("Peer uses older OFFGRID version · update both phones")
            frame.startsWith("M|") -> {
                val parts = frame.split('|', limit = 3)
                if (parts.size != 3) return
                val key = sessionKey ?: return
                val id = parts[1]
                val encrypted = runCatching { Base64.decode(parts[2], Base64.NO_WRAP) }.getOrNull() ?: return
                val plain = runCatching { decrypt(key, encrypted) }.getOrNull() ?: return
                val text = plain.toString(Charsets.UTF_8)
                onIncomingMessage(id, text)
                sendFrame("A|$id")
            }
            frame.startsWith("A|") -> {
                val id = frame.substringAfter("A|")
                if (id.isNotBlank()) onDelivered(id)
            }
        }
    }

    private fun handleSignedHello(frame: String) {
        val parts = frame.split('|', limit = 5)
        if (parts.size != 5) return
        val deviceId = parts[1]
        val ephemeralPubB64 = parts[2]
        val identityPubB64 = parts[3]
        val signatureBytes = runCatching { Base64.decode(parts[4], Base64.NO_WRAP) }.getOrNull() ?: return
        val remoteEphemeral = runCatching { Base64.decode(ephemeralPubB64, Base64.NO_WRAP) }.getOrNull() ?: return
        val remoteIdentity = runCatching { Base64.decode(identityPubB64, Base64.NO_WRAP) }.getOrNull() ?: return
        val body = helloBody(deviceId, ephemeralPubB64, identityPubB64)

        if (!verifyIdentity(remoteIdentity, body.toByteArray(Charsets.UTF_8), signatureBytes)) {
            onState("Identity signature rejected · connection blocked")
            sessionKey = null
            return
        }

        val localPair = localEphemeralKeyPair ?: return
        sessionKey = deriveSessionKey(localPair, remoteEphemeral, remoteIdentity)
        remoteDeviceId = deviceId
        reconnectAttempt = 0
        val fingerprint = identityFingerprint(remoteIdentity)
        val safetyCode = safetyCode(identityKeyPair.public.encoded, remoteIdentity)
        onState("Secure BLE session ready · signed identity")
        onSecurePeer(
            SecurePeer(
                deviceId = deviceId,
                identityFingerprint = fingerprint,
                safetyCode = safetyCode,
                address = activeRemote?.let { safeAddress(it) }.orEmpty()
            )
        )
        if (!helloSent) sendHello()
    }

    private fun sendFrame(frame: String) {
        val bytes = frame.toByteArray(Charsets.UTF_8)
        val frameId = nextFrameId()
        val total = ((bytes.size + PACKET_PAYLOAD_SIZE - 1) / PACKET_PAYLOAD_SIZE).coerceAtLeast(1)
        if (total > 255) {
            onState("Message too large")
            return
        }
        var offset = 0
        for (index in 0 until total) {
            val count = minOf(PACKET_PAYLOAD_SIZE, bytes.size - offset)
            val packet = ByteArray(HEADER_SIZE + count)
            packet[0] = MAGIC_1
            packet[1] = MAGIC_2
            packet[2] = ((frameId shr 8) and 0xff).toByte()
            packet[3] = (frameId and 0xff).toByte()
            packet[4] = index.toByte()
            packet[5] = total.toByte()
            if (count > 0) System.arraycopy(bytes, offset, packet, HEADER_SIZE, count)
            offset += count
            enqueuePacket(packet)
        }
    }

    private fun enqueuePacket(packet: ByteArray) {
        when (role) {
            Role.CLIENT -> {
                clientQueue.add(packet)
                pumpClientQueue()
            }
            Role.SERVER -> {
                serverQueue.add(packet)
                pumpServerQueue()
            }
            Role.NONE -> onState("No active peer")
        }
    }

    private fun pumpClientQueue() {
        if (clientWriteInFlight) return
        val gatt = clientGatt ?: return
        val characteristic = clientCharacteristic ?: return
        val packet = clientQueue.poll() ?: return
        clientWriteInFlight = true
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(characteristic, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = packet
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
        if (!ok) {
            clientWriteInFlight = false
            clientQueue.addFirst(packet)
            onState("BLE write failed")
        }
    }

    private fun pumpServerQueue() {
        if (serverNotifyInFlight) return
        val server = gattServer ?: return
        val characteristic = serverCharacteristic ?: return
        val device = activeRemote ?: return
        val packet = serverQueue.poll() ?: return
        serverNotifyInFlight = true
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            server.notifyCharacteristicChanged(device, characteristic, false, packet) == 0
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = packet
            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(device, characteristic, false)
        }
        if (!ok) {
            serverNotifyInFlight = false
            serverQueue.addFirst(packet)
            onState("BLE notify failed")
        }
    }

    private fun receivePacket(device: BluetoothDevice, packet: ByteArray) {
        if (packet.size < HEADER_SIZE || packet[0] != MAGIC_1 || packet[1] != MAGIC_2) return
        val frameId = ((packet[2].toInt() and 0xff) shl 8) or (packet[3].toInt() and 0xff)
        val index = packet[4].toInt() and 0xff
        val total = packet[5].toInt() and 0xff
        if (total <= 0 || index >= total) return
        val key = "${safeAddress(device)}:$frameId"
        val assembly = assemblers.getOrPut(key) { FrameAssembly(total) }
        if (assembly.total != total) {
            assemblers.remove(key)
            return
        }
        assembly.parts[index] = packet.copyOfRange(HEADER_SIZE, packet.size)
        if (assembly.parts.all { it != null }) {
            val size = assembly.parts.sumOf { it!!.size }
            val merged = ByteArray(size)
            var offset = 0
            assembly.parts.forEach { part ->
                val p = part!!
                System.arraycopy(p, 0, merged, offset, p.size)
                offset += p.size
            }
            assemblers.remove(key)
            handleFrame(merged.toString(Charsets.UTF_8))
        }
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (clientGatt !== gatt) {
                runCatching { gatt.close() }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                onState("Connected · negotiating GATT")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handleClientDisconnect(gatt, status)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onState("GATT service discovery failed: $status")
                runCatching { gatt.disconnect() }
                return
            }
            val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(DATA_UUID)
            if (characteristic == null) {
                onState("OFFGRID chat service not found")
                runCatching { gatt.disconnect() }
                return
            }
            clientCharacteristic = characteristic
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor == null) {
                onState("OFFGRID notify descriptor missing")
                runCatching { gatt.disconnect() }
                return
            }
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CCCD_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                onState("Connected · signed identity handshake")
                sendHello()
            } else if (descriptor.uuid == CCCD_UUID) {
                onState("Notify setup failed: $status")
                runCatching { gatt.disconnect() }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            receivePacket(gatt.device, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            characteristic.value?.let { receivePacket(gatt.device, it) }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            clientWriteInFlight = false
            if (status != BluetoothGatt.GATT_SUCCESS) onState("BLE write status $status")
            pumpClientQueue()
        }
    }

    private fun handleClientDisconnect(gatt: BluetoothGatt, status: Int) {
        runCatching { gatt.close() }
        clientGatt = null
        clientCharacteristic = null
        activeRemote = null
        role = Role.NONE
        clearTransportQueues()
        resetSessionCrypto()

        val target = reconnectTarget
        if (reconnectEnabled && target != null && reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempt += 1
            val delay = RECONNECT_BASE_DELAY_MS * reconnectAttempt
            onState("Disconnected ($status) · retry $reconnectAttempt/$MAX_RECONNECT_ATTEMPTS in ${delay / 1000}s")
            reconnectHandler.postDelayed({
                if (reconnectEnabled && clientGatt == null) openClient(target, true)
            }, delay)
        } else {
            onState(if (reconnectEnabled) "Disconnected · reconnect limit reached" else "Disconnected")
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (role != Role.NONE) {
                    gattServer?.cancelConnection(device)
                    return
                }
                reconnectHandler.removeCallbacksAndMessages(null)
                reconnectEnabled = false
                reconnectTarget = null
                reconnectAttempt = 0
                role = Role.SERVER
                activeRemote = device
                clearTransportQueues()
                resetSessionCrypto()
                onState("Incoming OFFGRID connection · signed identity handshake")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED && activeRemote?.address == device.address) {
                activeRemote = null
                role = Role.NONE
                clearTransportQueues()
                resetSessionCrypto()
                onState("Peer disconnected")
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            if (descriptor.uuid == CCCD_UUID) sendHello()
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            if (characteristic.uuid == DATA_UUID) receivePacket(device, value)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            serverNotifyInFlight = false
            if (status != BluetoothGatt.GATT_SUCCESS) onState("BLE notify status $status")
            pumpServerQueue()
        }
    }

    private fun loadOrCreateIdentityKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(IDENTITY_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
        if (existing != null) {
            return KeyPair(existing.certificate.publicKey, existing.privateKey)
        }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            IDENTITY_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    private fun generateEcKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return generator.generateKeyPair()
    }

    private fun signIdentity(payload: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(identityKeyPair.private)
        signature.update(payload)
        return signature.sign()
    }

    private fun verifyIdentity(publicEncoded: ByteArray, payload: ByteArray, signatureBytes: ByteArray): Boolean {
        return runCatching {
            val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicEncoded))
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(publicKey)
            signature.update(payload)
            signature.verify(signatureBytes)
        }.getOrDefault(false)
    }

    private fun deriveSessionKey(local: KeyPair, remoteEncoded: ByteArray, remoteIdentity: ByteArray): SecretKey {
        val factory = KeyFactory.getInstance("EC")
        val remote = factory.generatePublic(X509EncodedKeySpec(remoteEncoded))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(local.private)
        agreement.doPhase(remote, true)
        val shared = agreement.generateSecret()
        val identityPair = listOf(
            Base64.encodeToString(identityKeyPair.public.encoded, Base64.NO_WRAP),
            Base64.encodeToString(remoteIdentity, Base64.NO_WRAP)
        ).sorted()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(SESSION_CONTEXT.toByteArray(Charsets.UTF_8))
        digest.update(shared)
        digest.update(identityPair[0].toByteArray(Charsets.UTF_8))
        digest.update(identityPair[1].toByteArray(Charsets.UTF_8))
        val material = digest.digest()
        return SecretKeySpec(material.copyOfRange(0, 16), "AES")
    }

    private fun helloBody(deviceId: String, ephemeralPub: String, identityPub: String): String =
        "$HELLO_CONTEXT|$deviceId|$ephemeralPub|$identityPub"

    private fun identityFingerprint(publicEncoded: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(publicEncoded)
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }
    }

    private fun shortFingerprint(full: String): String = full.take(16).chunked(4).joinToString("-")

    private fun safetyCode(localIdentity: ByteArray, remoteIdentity: ByteArray): String {
        val pair = listOf(
            Base64.encodeToString(localIdentity, Base64.NO_WRAP),
            Base64.encodeToString(remoteIdentity, Base64.NO_WRAP)
        ).sorted()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(SAFETY_CONTEXT.toByteArray(Charsets.UTF_8))
        digest.update(pair[0].toByteArray(Charsets.UTF_8))
        digest.update(pair[1].toByteArray(Charsets.UTF_8))
        return digest.digest().take(8)
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }
            .chunked(4)
            .joinToString("-")
    }

    private fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain)
        return ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array()
    }

    private fun decrypt(key: SecretKey, payload: ByteArray): ByteArray {
        require(payload.size > 12)
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun safeAddress(device: BluetoothDevice): String = runCatching { device.address }.getOrDefault("peer")

    private fun nextFrameId(): Int {
        frameCounter = (frameCounter + 1) and 0xffff
        if (frameCounter == 0) frameCounter = 1
        return frameCounter
    }

    private enum class Role { NONE, CLIENT, SERVER }
    private class FrameAssembly(val total: Int) {
        val parts: Array<ByteArray?> = arrayOfNulls(total)
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("9f92b6a8-d601-4db8-a2fc-0ff67f0a6b71")
        private val DATA_UUID: UUID = UUID.fromString("3f3a3e40-5c72-4f0e-9f08-5b7b4ebc9b10")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val HEADER_SIZE = 6
        private const val PACKET_PAYLOAD_SIZE = 14
        private const val MAGIC_1: Byte = 0x4f
        private const val MAGIC_2: Byte = 0x47
        private const val MAX_RECONNECT_ATTEMPTS = 3
        private const val RECONNECT_BASE_DELAY_MS = 1_500L
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val IDENTITY_KEY_ALIAS = "offgrid-phase1-identity"
        private const val HELLO_CONTEXT = "OFFGRID-H2"
        private const val SESSION_CONTEXT = "OFFGRID-PHASE1.3"
        private const val SAFETY_CONTEXT = "OFFGRID-SAFETY1"
    }
}
