package com.offgrid.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MainActivity : Activity() {

    private val serviceUuid = ParcelUuid(BleChatManager.SERVICE_UUID)
    private lateinit var statusView: TextView
    private lateinit var peersContainer: LinearLayout
    private lateinit var myIdView: TextView

    private lateinit var meshStatusView: TextView
    private lateinit var meshNameInput: EditText
    private lateinit var meshCodeInput: EditText
    private lateinit var meshJoinButton: Button
    private lateinit var meshLog: TextView
    private lateinit var meshScroll: ScrollView
    private lateinit var meshMessageInput: EditText
    private lateinit var meshSendButton: Button

    private lateinit var chatPanel: LinearLayout
    private lateinit var chatTitle: TextView
    private lateinit var identityView: TextView
    private lateinit var verifyButton: Button
    private lateinit var chatLog: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var pageScroll: ScrollView
    private lateinit var composerBar: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    private lateinit var chatManager: BleChatManager
    private lateinit var chatStore: ChatStore
    private lateinit var meshStore: MeshStore

    private var running = false
    @Volatile private var activePeerId: String? = null
    private var activePeerFingerprint: String? = null
    private var activeSafetyCode: String? = null
    private var activePeerVerified = false
    @Volatile private var activeIdentityChanged = false

    private val peers = linkedMapOf<String, PeerInfo>()
    private val messages = mutableListOf<ChatEntry>()
    private val meshTransportAcks = ConcurrentHashMap<String, MeshTransit>()
    private val meshInFlight = ConcurrentHashMap.newKeySet<String>()
    private val localId by lazy { deviceId() }

    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            runOnUiThread { setStatus("BLE advertising + scanning · tap one node to connect") }
        }

        override fun onStartFailure(errorCode: Int) {
            runOnUiThread { setStatus("Scanning active · advertise error $errorCode") }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = runCatching { result.device.address }.getOrElse { return }
            val id = address.replace(":", "").takeLast(12)
            if (id.isBlank()) return
            peers[address] = PeerInfo(id, address, result.device, result.rssi, System.currentTimeMillis())
            pruneAndRender()
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread { setStatus("Scan failed: $errorCode") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatStore = ChatStore(this)
        meshStore = MeshStore(this, localId)
        buildUi()

        chatManager = BleChatManager(
            context = this,
            localDeviceId = localId,
            onState = { state -> runOnUiThread { setStatus(state) } },
            onSecurePeer = { peer ->
                val check = chatStore.observePeerIdentity(
                    peerId = peer.deviceId,
                    address = peer.address.ifBlank { null },
                    fingerprint = peer.identityFingerprint
                )
                val restored = chatStore.loadMessages(peer.deviceId).map {
                    ChatEntry(it.id, it.mine, it.text, it.delivered, it.createdAt)
                }
                val accepted = check.state != ChatStore.IdentityState.CHANGED
                if (accepted) activePeerId = peer.deviceId
                runOnUiThread {
                    applySecurePeer(peer, check, restored)
                    if (accepted) syncMeshWithPeer(peer.deviceId)
                }
                accepted
            },
            onIncomingMessage = { id, text -> handleIncomingTransportMessage(id, text) },
            onDelivered = { id -> handleTransportDelivered(id) }
        )

        myIdView.text = "This device: $localId\nIdentity: ${chatManager.localIdentityFingerprint()}"
        meshStore.currentGroup()?.let { meshNameInput.setText(it.name) }
        renderMesh()
    }

    override fun onDestroy() {
        stopDiscovery()
        chatManager.close()
        chatStore.close()
        meshStore.close()
        super.onDestroy()
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        pageScroll = ScrollView(this).apply {
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
        }
        pageScroll.addView(content)
        screen.addView(
            pageScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        content.addView(TextView(this).apply {
            text = "OFFGRID"
            textSize = 34f
        })
        content.addView(TextView(this).apply {
            text = "Phase 2.0 · Store & Forward Mesh"
            textSize = 16f
        })

        myIdView = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(14), 0, 0)
        }
        content.addView(myIdView)

        statusView = TextView(this).apply {
            text = "Status: Idle"
            textSize = 15f
            setPadding(0, dp(6), 0, dp(10))
        }
        content.addView(statusView)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        buttonRow.addView(Button(this).apply {
            text = "START DISCOVERY"
            setOnClickListener { requestPermissionsAndStart() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttonRow.addView(Button(this).apply {
            text = "STOP"
            setOnClickListener { stopDiscovery() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(buttonRow)

        content.addView(TextView(this).apply {
            text = "MESH GROUP · 3 PHONE TEST"
            textSize = 20f
            setPadding(0, dp(18), 0, dp(4))
        })
        content.addView(TextView(this).apply {
            text = "Use the SAME group name + group code on all 3 phones. The code never travels in relay packets."
            textSize = 13f
            setPadding(0, 0, 0, dp(6))
        })

        meshNameInput = EditText(this).apply {
            hint = "Group name · example: TEST TEAM"
            maxLines = 1
        }
        content.addView(meshNameInput)

        meshCodeInput = EditText(this).apply {
            hint = "Group code · minimum 6 characters"
            maxLines = 1
        }
        content.addView(meshCodeInput)

        meshJoinButton = Button(this).apply {
            text = "JOIN / SAVE MESH GROUP"
            isAllCaps = false
            setOnClickListener { joinMeshGroup() }
        }
        content.addView(meshJoinButton)

        meshStatusView = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(6), 0, dp(6))
        }
        content.addView(meshStatusView)

        meshLog = TextView(this).apply {
            textSize = 15f
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        meshScroll = ScrollView(this).apply { addView(meshLog) }
        content.addView(
            meshScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180))
        )

        val meshComposer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(4))
        }
        meshMessageInput = EditText(this).apply {
            hint = "Mesh group message"
            maxLines = 3
            minLines = 1
        }
        meshComposer.addView(
            meshMessageInput,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        meshSendButton = Button(this).apply {
            text = "MESH SEND"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { sendMeshMessage() }
        }
        meshComposer.addView(meshSendButton)
        content.addView(meshComposer)

        content.addView(TextView(this).apply {
            text = "Nearby OFFGRID nodes"
            textSize = 20f
            setPadding(0, dp(16), 0, dp(3))
        })
        content.addView(TextView(this).apply {
            text = "For relay test: connect A→B, send MESH message, then on B tap C. Stored mesh packets sync automatically."
            textSize = 13f
            setPadding(0, 0, 0, dp(6))
        })

        peersContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(peersContainer)

        chatPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(16), 0, 0)
        }
        chatTitle = TextView(this).apply {
            text = "Encrypted direct chat"
            textSize = 20f
        }
        chatPanel.addView(chatTitle)
        chatPanel.addView(TextView(this).apply {
            text = "Direct chat: signed ephemeral ECDH + AES-GCM. Mesh payloads use a separate group key."
            textSize = 12f
            setPadding(0, dp(3), 0, dp(5))
        })

        identityView = TextView(this).apply {
            text = "Waiting for signed peer identity…"
            textSize = 14f
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        chatPanel.addView(identityView)

        verifyButton = Button(this).apply {
            text = "MARK IDENTITY VERIFIED"
            isAllCaps = false
            visibility = View.GONE
            setOnClickListener { verifyCurrentPeer() }
        }
        chatPanel.addView(verifyButton)

        chatLog = TextView(this).apply {
            text = "Secure handshake ready. Send a direct message."
            textSize = 15f
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        chatScroll = ScrollView(this).apply { addView(chatLog) }
        chatPanel.addView(
            chatScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150))
        )
        content.addView(chatPanel)

        composerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        messageInput = EditText(this).apply {
            hint = "Direct offline message"
            maxLines = 3
            minLines = 1
        }
        composerBar.addView(
            messageInput,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        sendButton = Button(this).apply {
            text = "SEND"
            isEnabled = false
            setOnClickListener { sendCurrentMessage() }
        }
        composerBar.addView(sendButton)
        screen.addView(
            composerBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        setContentView(screen)
        renderPeers()
    }

    private fun joinMeshGroup() {
        val name = meshNameInput.text.toString().trim()
        val code = meshCodeInput.text.toString().trim()
        if (name.isBlank() || code.length < 6) {
            meshStatusView.text = "Enter a group name and a group code of at least 6 characters."
            return
        }
        val config = runCatching { meshStore.configureGroup(name, code) }.getOrNull()
        if (config == null) {
            meshStatusView.text = "Could not configure mesh group."
            return
        }
        meshCodeInput.setText("")
        renderMesh()
        meshStatusView.text = "✓ Joined ${config.name} · Group ID ${config.groupId}\nPut the SAME name + code on the other phones."
    }

    private fun sendMeshMessage() {
        val text = meshMessageInput.text.toString().trim()
        if (text.isBlank()) return
        val envelope = runCatching { meshStore.createMessage(text) }.getOrNull()
        if (envelope == null) {
            meshStatusView.text = "Join a mesh group first. Message limit is 600 characters."
            return
        }
        meshMessageInput.setText("")
        renderMesh()
        val peerId = activePeerId
        if (peerId != null && chatManager.isSecure() && !activeIdentityChanged) {
            syncMeshWithPeer(peerId)
        } else {
            meshStatusView.text = "Queued locally · connect to any OFFGRID peer to carry this encrypted message."
        }
    }

    private fun syncMeshWithPeer(peerId: String) {
        if (!chatManager.isSecure() || activeIdentityChanged || activePeerId != peerId) return
        val pending = runCatching { meshStore.pendingForPeer(peerId) }.getOrDefault(emptyList())
        var sent = 0
        pending.forEach { envelope ->
            val inFlightKey = "$peerId|${envelope.id}"
            if (!meshInFlight.add(inFlightKey)) return@forEach
            val transportId = chatManager.sendText(meshWire(envelope))
            if (transportId == null) {
                meshInFlight.remove(inFlightKey)
                return@forEach
            }
            meshTransportAcks[transportId] = MeshTransit(peerId, envelope.id)
            sent += 1
        }
        renderMesh()
        meshStatusView.text = when {
            sent > 0 -> "Mesh sync: sending $sent encrypted packet(s) to OFFGRID-${peerId.takeLast(6)}."
            pending.isEmpty() -> meshGroupSummary("Mesh sync complete · nothing new for this peer.")
            else -> meshGroupSummary("Mesh packets already in flight.")
        }
    }

    private fun handleIncomingTransportMessage(transportId: String, text: String) {
        val envelope = parseMeshWire(text)
        if (envelope != null) {
            val fromPeer = activePeerId ?: return
            val result = runCatching { meshStore.receiveEnvelope(envelope, fromPeer) }.getOrNull() ?: return
            runOnUiThread {
                renderMesh()
                meshStatusView.text = if (result.isNew) {
                    "✓ Mesh packet stored from OFFGRID-${fromPeer.takeLast(6)} · hop ${envelope.hopCount + 1}/${envelope.maxHops}."
                } else {
                    meshGroupSummary("Mesh duplicate/expired packet ignored safely.")
                }
            }
            return
        }

        runOnUiThread {
            if (activeIdentityChanged) return@runOnUiThread
            val peerId = activePeerId ?: return@runOnUiThread
            if (messages.none { it.id == transportId }) {
                val createdAt = System.currentTimeMillis()
                messages += ChatEntry(transportId, false, text, true, createdAt)
                chatStore.saveMessage(
                    ChatStore.StoredMessage(transportId, peerId, false, text, true, createdAt)
                )
                renderChat()
            }
        }
    }

    private fun handleTransportDelivered(transportId: String) {
        val meshTransit = meshTransportAcks.remove(transportId)
        if (meshTransit != null) {
            meshInFlight.remove("${meshTransit.peerId}|${meshTransit.messageId}")
            runCatching { meshStore.markAck(meshTransit.peerId, meshTransit.messageId) }
            runOnUiThread {
                renderMesh()
                meshStatusView.text = "✓ Mesh packet copied to OFFGRID-${meshTransit.peerId.takeLast(6)} · stored for onward relay."
            }
            return
        }

        runOnUiThread {
            val index = messages.indexOfFirst { it.id == transportId }
            if (index >= 0) {
                messages[index] = messages[index].copy(delivered = true)
                chatStore.markDelivered(transportId)
                renderChat()
            }
        }
    }

    private fun meshWire(e: MeshStore.Envelope): String =
        "$MESH_WIRE|${e.id}|${e.groupId}|${e.senderId}|${e.createdAt}|${e.expiresAt}|${e.hopCount}|${e.maxHops}|${e.cipherText}"

    private fun parseMeshWire(text: String): MeshStore.Envelope? {
        if (!text.startsWith("$MESH_WIRE|")) return null
        val parts = text.split('|', limit = 9)
        if (parts.size != 9 || parts[0] != MESH_WIRE) return null
        return MeshStore.Envelope(
            id = parts[1],
            groupId = parts[2],
            senderId = parts[3],
            createdAt = parts[4].toLongOrNull() ?: return null,
            expiresAt = parts[5].toLongOrNull() ?: return null,
            hopCount = parts[6].toIntOrNull() ?: return null,
            maxHops = parts[7].toIntOrNull() ?: return null,
            cipherText = parts[8]
        )
    }

    private fun renderMesh() {
        val config = runCatching { meshStore.currentGroup() }.getOrNull()
        meshSendButton.isEnabled = config != null
        if (config == null) {
            meshStatusView.text = "No mesh group yet. Create the same group on all 3 phones."
            meshLog.text = "Mesh messages will appear here."
            return
        }

        val stored = runCatching { meshStore.queueCount() }.getOrDefault(0)
        val readable = runCatching { meshStore.readableMessages() }.getOrDefault(emptyList())
        meshStatusView.text = "Group: ${config.name} · ID ${config.groupId}\nStored relay packets: $stored"
        meshLog.text = if (readable.isEmpty()) {
            "Group ready. Send a MESH message, then connect through another phone."
        } else {
            readable.joinToString("\n\n") { msg ->
                val who = if (msg.mine) "You" else "OFFGRID-${msg.senderId.takeLast(6)}"
                val route = if (msg.mine) {
                    "origin · synced to ${msg.syncedPeers} peer(s)"
                } else {
                    "received at hop ${msg.hopCount}"
                }
                "$who: ${msg.text}\nMesh: $route"
            }
        }
        meshScroll.post { meshScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun meshGroupSummary(prefix: String): String {
        val config = runCatching { meshStore.currentGroup() }.getOrNull() ?: return prefix
        val stored = runCatching { meshStore.queueCount() }.getOrDefault(0)
        return "$prefix\n${config.name} · ${config.groupId} · stored $stored"
    }

    private fun applySecurePeer(
        peer: BleChatManager.SecurePeer,
        check: ChatStore.PeerIdentityCheck,
        restored: List<ChatEntry>
    ) {
        activePeerId = peer.deviceId
        activePeerFingerprint = peer.identityFingerprint
        activeSafetyCode = peer.safetyCode
        activePeerVerified = check.verified
        activeIdentityChanged = check.state == ChatStore.IdentityState.CHANGED
        meshTransportAcks.clear()
        meshInFlight.clear()

        messages.clear()
        messages += restored
        chatTitle.text = "Encrypted direct chat · OFFGRID-${peer.deviceId.takeLast(6)}"
        chatPanel.visibility = View.VISIBLE
        renderIdentityPanel()
        renderChat()

        if (activeIdentityChanged) {
            composerBar.visibility = View.GONE
            sendButton.isEnabled = false
            setStatus("IDENTITY CHANGED · direct chat and mesh sync blocked")
        } else {
            composerBar.visibility = View.VISIBLE
            sendButton.isEnabled = true
            setStatus(
                when {
                    activePeerVerified -> "Secure session ready · verified peer · mesh sync available"
                    messages.isEmpty() -> "Secure session ready · identity not verified yet · mesh sync available"
                    else -> "Secure session ready · ${messages.size} local direct messages restored"
                }
            )
        }
        pageScroll.post { pageScroll.fullScroll(View.FOCUS_DOWN) }
        renderPeers()
    }

    private fun renderIdentityPanel() {
        val fingerprint = activePeerFingerprint?.take(16)?.chunked(4)?.joinToString("-") ?: "unknown"
        val safety = activeSafetyCode ?: "----"
        identityView.text = when {
            activeIdentityChanged -> {
                "⚠ IDENTITY CHANGED\nPeer fingerprint: $fingerprint\nThe saved identity for this OFFGRID ID is different. Connection is blocked."
            }
            activePeerVerified -> {
                "✓ VERIFIED IDENTITY\nSafety code: $safety\nPeer fingerprint: $fingerprint\nIdentity matches the one you previously verified."
            }
            else -> {
                "UNVERIFIED IDENTITY\nSafety code: $safety\nPeer fingerprint: $fingerprint\nCompare the Safety Code on BOTH phones. Only if they match, tap MARK IDENTITY VERIFIED."
            }
        }
        verifyButton.visibility = if (!activeIdentityChanged && !activePeerVerified) View.VISIBLE else View.GONE
    }

    private fun verifyCurrentPeer() {
        if (activeIdentityChanged) return
        val peerId = activePeerId ?: return
        val fingerprint = activePeerFingerprint ?: return
        if (chatStore.markPeerVerified(peerId, fingerprint)) {
            activePeerVerified = true
            renderIdentityPanel()
            renderPeers()
            setStatus("Identity verified · future key changes will be blocked")
        } else {
            setStatus("Could not verify peer identity")
        }
    }

    private fun sendCurrentMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return
        if (activeIdentityChanged) {
            setStatus("Identity changed · chat blocked")
            return
        }
        if (!chatManager.isSecure()) {
            setStatus("Secure handshake not ready")
            sendButton.isEnabled = false
            return
        }
        val peerId = activePeerId ?: run {
            setStatus("Peer identity not ready")
            return
        }
        val id = chatManager.sendText(text)
        if (id == null) {
            setStatus("Could not send message")
            return
        }
        val createdAt = System.currentTimeMillis()
        messages += ChatEntry(id, true, text, false, createdAt)
        chatStore.saveMessage(
            ChatStore.StoredMessage(id, peerId, true, text, false, createdAt)
        )
        messageInput.setText("")
        renderChat()
    }

    private fun renderChat() {
        chatLog.text = if (messages.isEmpty()) {
            "Secure handshake ready. Send a direct message."
        } else {
            messages.joinToString("\n\n") { msg ->
                if (msg.mine) {
                    "You: ${msg.text}\n${if (msg.delivered) "✓ Delivered" else "… Sending"}"
                } else {
                    "Peer: ${msg.text}"
                }
            }
        }
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun deviceId(): String {
        val prefs = getSharedPreferences("offgrid_identity", Context.MODE_PRIVATE)
        val existing = prefs.getString("device_id", null)
        if (existing != null) return existing.replace("-", "").take(12)
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", created).apply()
        return created.replace("-", "").take(12)
    }

    private fun requestPermissionsAndStart() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startDiscovery()
        else requestPermissions(missing.toTypedArray(), REQUEST_BLUETOOTH)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startDiscovery()
            else setStatus("Bluetooth permission denied")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        if (running) return
        val bluetoothAdapter = adapter ?: run {
            setStatus("Bluetooth unavailable on this device")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            setStatus("Turn Bluetooth on, then tap START DISCOVERY")
            return
        }
        if (!chatManager.startServer()) {
            setStatus("Could not start OFFGRID GATT server")
            return
        }
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            setStatus("BLE scanner unavailable")
            return
        }
        running = true
        peers.clear()
        renderPeers()

        val filter = ScanFilter.Builder().setServiceUuid(serviceUuid).build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), scanSettings, scanCallback)

        if (bluetoothAdapter.isMultipleAdvertisementSupported) {
            val advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            val advertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .build()
            val advertiseData = AdvertiseData.Builder()
                .addServiceUuid(serviceUuid)
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build()
            advertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
            setStatus("Starting BLE advertising + scanning…")
        } else {
            setStatus("Scanning active · this phone cannot BLE-advertise")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(peer: PeerInfo) {
        if (!hasBluetoothPermissions()) {
            setStatus("Bluetooth permission required")
            return
        }
        activePeerId = null
        activePeerFingerprint = null
        activeSafetyCode = null
        activePeerVerified = false
        activeIdentityChanged = false
        meshTransportAcks.clear()
        meshInFlight.clear()

        val knownPeer = chatStore.peerForAddress(peer.address)
        chatPanel.visibility = View.VISIBLE
        composerBar.visibility = View.GONE
        verifyButton.visibility = View.GONE
        identityView.text = "Waiting for signed peer identity…"
        chatTitle.text = if (knownPeer != null) {
            "Reconnecting · OFFGRID-${knownPeer.peerId.takeLast(6)}"
        } else {
            "Connecting · OFFGRID-${peer.id.takeLast(6)}"
        }
        sendButton.isEnabled = false
        messages.clear()
        renderChat()
        pageScroll.post { pageScroll.fullScroll(View.FOCUS_DOWN) }
        chatManager.connect(peer.device)
    }

    @SuppressLint("MissingPermission")
    private fun stopDiscovery() {
        if (!running) return
        if (hasBluetoothPermissions()) {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        }
        running = false
        setStatus(if (chatManager.isSecure()) "Discovery stopped · active connection remains" else "Discovery stopped")
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ).all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun pruneAndRender() {
        val cutoff = System.currentTimeMillis() - PEER_TIMEOUT_MS
        peers.entries.removeIf { it.value.lastSeen < cutoff }
        runOnUiThread { renderPeers() }
    }

    private fun renderPeers() {
        peersContainer.removeAllViews()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        if (peers.isEmpty()) {
            peersContainer.addView(TextView(this).apply {
                text = "No OFFGRID nodes detected yet.\nTurn Bluetooth on and START DISCOVERY on the other phones."
                textSize = 15f
            })
            return
        }

        peers.values.sortedByDescending { it.rssi }.forEach { peer ->
            val knownPeer = chatStore.peerForAddress(peer.address)
            peersContainer.addView(Button(this).apply {
                text = when {
                    knownPeer?.verified == true -> {
                        "OFFGRID-${knownPeer.peerId.takeLast(6)}   ·   ${peer.rssi} dBm\n✓ VERIFIED PEER · TAP TO CONNECT"
                    }
                    knownPeer != null -> {
                        "OFFGRID-${knownPeer.peerId.takeLast(6)}   ·   ${peer.rssi} dBm\nKNOWN PEER · TAP TO CONNECT"
                    }
                    else -> {
                        "OFFGRID-${peer.id.takeLast(6)}   ·   ${peer.rssi} dBm\nTAP TO CONNECT"
                    }
                }
                isAllCaps = false
                setOnClickListener { connectTo(peer) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            })
        }
    }

    private fun setStatus(value: String) {
        statusView.text = "Status: $value"
    }

    private data class PeerInfo(
        val id: String,
        val address: String,
        val device: BluetoothDevice,
        val rssi: Int,
        val lastSeen: Long
    )

    private data class ChatEntry(
        val id: String,
        val mine: Boolean,
        val text: String,
        val delivered: Boolean,
        val createdAt: Long
    )

    private data class MeshTransit(val peerId: String, val messageId: String)

    companion object {
        private const val REQUEST_BLUETOOTH = 1001
        private const val PEER_TIMEOUT_MS = 20_000L
        private const val MESH_WIRE = "@OGM1"
    }
}
