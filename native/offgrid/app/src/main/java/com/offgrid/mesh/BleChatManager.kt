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
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
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
    private val onSecurePeer: (String) -> Unit,
    private val onIncomingMessage: (String, String) -> Unit,
    private val onDelivered: (String) -> Unit
) {
    private val bluetoothManager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
    private var gattServer: BluetoothGattServer? = null
    private var serverCharacteristic: BluetoothGattCharacteristic? = null
    private var clientGatt: BluetoothGatt? = null
    private var clientCharacteristic: BluetoothGattCharacteristic? = null
    private var activeRemote: BluetoothDevice? = null
    private var role: Role = Role.NONE

    private var localKeyPair: KeyPair? = null
    private var sessionKey: SecretKey? = null
    private var remoteDeviceId: String? = null
    private var helloSent = false

    private val clientQueue = ArrayDeque<ByteArray>()
    private var clientWriteInFlight = false
    private val serverQueue = ArrayDeque<ByteArray>()
    private var serverNotifyInFlight = false
    private var frameCounter = 1
    private val assemblers = mutableMapOf<String, FrameAssembly>()
    private val secureRandom = SecureRandom()

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
        role = Role.CLIENT
        activeRemote = device
        resetCrypto()
        onState("Connecting to ${safeAddress(device)}…")
        clientGatt = device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        runCatching { clientGatt?.disconnect() }
        runCatching { clientGatt?.close() }
        clientGatt = null
        clientCharacteristic = null
        if (role == Role.SERVER) {
            activeRemote?.let { runCatching { gattServer?.cancelConnection(it) } }
        }
        activeRemote = null
        role = Role.NONE
        clientQueue.clear()
        serverQueue.clear()
        clientWriteInFlight = false
        serverNotifyInFlight = false
        assemblers.clear()
        resetCrypto()
    }

    fun close() {
        disconnect()
        runCatching { gattServer?.close() }
        gattServer = null
        serverCharacteristic = null
    }

    fun isSecure(): Boolean = sessionKey != null

    fun sendText(text: String): String? {
        val key = sessionKey ?: return null
        if (text.isBlank()) return null
        val id = UUID.randomUUID().toString()
        val encrypted = encrypt(key, text.toByteArray(Charsets.UTF_8))
        val frame = "M|$id|${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
        sendFrame(frame)
        return id
    }

    private fun resetCrypto() {
        localKeyPair = generateEcKeyPair()
        sessionKey = null
        remoteDeviceId = null
        helloSent = false
    }

    private fun sendHello() {
        if (helloSent) return
        val pair = localKeyPair ?: generateEcKeyPair().also { localKeyPair = it }
        val pub = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        helloSent = true
        sendFrame("H|$localDeviceId|$pub")
    }

    private fun handleFrame(frame: String) {
        when {
            frame.startsWith("H|") -> {
                val parts = frame.split('|', limit = 3)
                if (parts.size != 3) return
                remoteDeviceId = parts[1]
                val remotePublic = runCatching { Base64.decode(parts[2], Base64.NO_WRAP) }.getOrNull() ?: return
                val localPair = localKeyPair ?: return
                sessionKey = deriveSessionKey(localPair, remotePublic)
                onState("Secure BLE session ready")
                onSecurePeer(parts[1])
                if (!helloSent) sendHello()
            }
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
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                onState("Connected · negotiating GATT")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onState("Disconnected")
                if (clientGatt === gatt) {
                    runCatching { gatt.close() }
                    clientGatt = null
                    clientCharacteristic = null
                    activeRemote = null
                    role = Role.NONE
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onState("GATT service discovery failed: $status")
                return
            }
            val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(DATA_UUID)
            if (characteristic == null) {
                onState("OFFGRID chat service not found")
                return
            }
            clientCharacteristic = characteristic
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor == null) {
                onState("OFFGRID notify descriptor missing")
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
                onState("Connected · secure handshake")
                sendHello()
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

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (role != Role.NONE) {
                    gattServer?.cancelConnection(device)
                    return
                }
                role = Role.SERVER
                activeRemote = device
                resetCrypto()
                onState("Incoming OFFGRID connection · secure handshake")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED && activeRemote?.address == device.address) {
                activeRemote = null
                role = Role.NONE
                serverQueue.clear()
                serverNotifyInFlight = false
                resetCrypto()
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

    private fun generateEcKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return generator.generateKeyPair()
    }

    private fun deriveSessionKey(local: KeyPair, remoteEncoded: ByteArray): SecretKey {
        val factory = KeyFactory.getInstance("EC")
        val remote = factory.generatePublic(X509EncodedKeySpec(remoteEncoded))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(local.private)
        agreement.doPhase(remote, true)
        val shared = agreement.generateSecret()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("OFFGRID-PHASE1".toByteArray(Charsets.UTF_8))
        val material = digest.digest(shared)
        return SecretKeySpec(material.copyOfRange(0, 16), "AES")
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
    }
}
