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
import android.os.Handler
import android.os.Looper
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

class Phase21Activity : Activity() {

    private val serviceUuid = ParcelUuid(BleChatManager.SERVICE_UUID)
    private val uiHandler = Handler(Looper.getMainLooper())

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
    private val directMessages = mutableListOf<ChatEntry>()
    private val meshTransportAcks = ConcurrentHashMap<String, MeshTransit>()
    private val meshInFlight = ConcurrentHashMap.newKeySet<String>()

    private var pendingSwitchPeer: PeerInfo? = null
    private var switchControlTransportId: String? = null
    private var switchGeneration = 0

    private val localId by lazy { deviceId() }
    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            runOnUiThread { setStatus("BLE advertising + scanning · ready for OFFGRID peers") }
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
            onState = { state ->
                runOnUiThread {
                    setStatus(state)
                    if (state.startsWith("Disconnected") || state == "Peer disconnected") {
                        sendButton.isEnabled = false
                        composerBar.visibility = View.GONE
                    }
                }
            },
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
        uiHandler.removeCallbacksAndMessages(null)
        stopDiscovery()
        chatManager.close()
        chatStore.close()
        meshStore.close()
        super.onDestroy()
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pageScroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
        }
        pageScroll.addView(content)
        screen.addView(pageScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        content.addView(TextView(this).apply {
            text = "OFFGRID"
            textSize = 34f
        })
        content.addView(TextView(this).apply {
            text = "Phase 2.1 · Stable 3-Phone Mesh Handoff"
            textSize = 16f
        })

        myIdView = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(myIdView)

        statusView = TextView(this).apply {
            text = "Status: Idle"
            textSize = 15f
            setPadding(0, dp(6), 0, dp(10))
        }
        content.addView(statusView)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(Button(this).apply {
            text = "START DISCOVERY"
            setOnClickListener { requestPermissionsAndStart() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(Button(this).apply {
            text = "STOP"
            setOnClickListener { stopDiscovery() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(controls)

        content.addView(TextView(this).apply {
            text = "MESH GROUP · 3 PHONE TEST"
            textSize = 20f
            setPadding(0, dp(16), 0, dp(3))
        })
        content.addView(TextView(this).apply {
            text = "Set the SAME group name + code on all phones. JOIN no longer interrupts an active BLE chat."
            textSize = 13f
        })

        meshNameInput = EditText(this).apply {
            hint = "Group name · example: PAPA SAUCE TEAM"
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
            setOnClickListener { joinMeshGroupAsync() }
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
        content.addView(meshScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)))

        val meshComposer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        meshMessageInput = EditText(this).apply {
            hint = "Mesh group message"
            maxLines = 3
            minLines = 1
        }
        meshComposer.addView(meshMessageInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
            text = "Relay test: A connects B and sends MESH. Then on B simply tap C. OFFGRID safely releases A before switching and carries the queued packet to C."
            textSize = 13f
            setPadding(0, 0, 0, dp(6))
        })

        peersContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(peersContainer)

        chatPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(14), 0, 0)
        }
        chatTitle = TextView(this).apply {
            text = "Encrypted direct chat"
            textSize = 20f
        }
        chatPanel.addView(chatTitle)

        identityView = TextView(this).apply {
            text = "Waiting for signed peer identity…"
            textSize = 14f
            setPadding(dp(8), dp(6), dp(8), dp(6))
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
            textSize = 15f
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        chatScroll = ScrollView(this).apply { addView(chatLog) }
        chatPanel.addView(chatScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)))
        content.addView(chatPanel)

        composerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        messageInput = EditText(this).apply {
            hint = "Direct offline message"
            maxLines = 3
            minLines = 1
        }
        composerBar.addView(messageInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sendButton = Button(this).apply {
            text = "SEND"
            isEnabled = false
            setOnClickListener { sendDirectMessage() }
        }
        composerBar.addView(sendButton)
        screen.addView(composerBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(screen)
        renderPeers()
        renderDirectChat()
    }

    private fun joinMeshGroupAsync() {
        val name = meshNameInput.text.toString().trim()
        val code = meshCodeInput.text.toString().trim()
        if (name.isBlank() || code.length < 6) {
            meshStatusView.text = "Enter a group name and a group code of at least 6 characters."
            return
        }

        meshJoinButton.isEnabled = false
        meshStatusView.text = "Preparing encrypted mesh group… direct BLE session stays untouched."

        Thread {
            val config = runCatching { meshStore.configureGroup(name, code) }.getOrNull()
            runOnUiThread {
                meshJoinButton.isEnabled = true
                if (config == null) {
                    meshStatusView.text = "Could not configure mesh group."
                    return@runOnUiThread
                }
                meshCodeInput.setText("")
                renderMesh()
                val peer = activePeerId
                if (peer != null && chatManager.isSecure() && !activeIdentityChanged) {
                    syncMeshWithPeer(peer)
                }
                meshStatusView.text = "✓ Joined ${config.name} · Group ID ${config.groupId}\nDirect chat remains connected. Use the SAME name + code on the other phones."
            }
        }.start()
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
        val peer = activePeerId
        if (peer != null && chatManager.isSecure() && !activeIdentityChanged) {
            syncMeshWithPeer(peer)
        } else {
            meshStatusView.text = "✓ Saved locally. Tap any nearby OFFGRID peer to carry this packet."
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
            sent > 0 -> "Sending $sent stored mesh packet(s) to OFFGRID-${peerId.takeLast(6)}…"
            pending.isEmpty() -> meshSummary("✓ Mesh sync complete with OFFGRID-${peerId.takeLast(6)}")
            else -> meshSummary("Mesh packet(s) already in flight")
        }
    }

    private fun handleIncomingTransportMessage(transportId: String, text: String) {
        if (text == CONTROL_RELEASE) {
            runOnUiThread {
                setStatus("Peer is switching relay link · releasing safely")
                sendButton.isEnabled = false
                composerBar.visibility = View.GONE
                uiHandler.postDelayed({
                    chatManager.disconnect()
                    clearActiveSessionUi("Link released · mesh packets remain stored")
                }, CONTROL_RELEASE_DELAY_MS)
            }
            return
        }

        val envelope = parseMeshWire(text)
        if (envelope != null) {
            val fromPeer = activePeerId ?: return
            val result = runCatching { meshStore.receiveEnvelope(envelope, fromPeer) }.getOrNull() ?: return
            runOnUiThread {
                renderMesh()
                meshStatusView.text = if (result.isNew) {
                    "✓ Mesh packet stored from OFFGRID-${fromPeer.takeLast(6)} · ready for next hop"
                } else {
                    meshSummary("Duplicate/expired packet ignored")
                }
            }
            return
        }

        runOnUiThread {
            if (activeIdentityChanged) return@runOnUiThread
            val peerId = activePeerId ?: return@runOnUiThread
            if (directMessages.none { it.id == transportId }) {
                val createdAt = System.currentTimeMillis()
                directMessages += ChatEntry(transportId, false, text, true, createdAt)
                chatStore.saveMessage(ChatStore.StoredMessage(transportId, peerId, false, text, true, createdAt))
                renderDirectChat()
            }
        }
    }

    private fun handleTransportDelivered(transportId: String) {
        if (transportId == switchControlTransportId) {
            switchControlTransportId = null
            runOnUiThread { performPendingSwitch() }
            return
        }

        val meshTransit = meshTransportAcks.remove(transportId)
        if (meshTransit != null) {
            meshInFlight.remove("${meshTransit.peerId}|${meshTransit.messageId}")
            runCatching { meshStore.markAck(meshTransit.peerId, meshTransit.messageId) }
            runOnUiThread {
                renderMesh()
                meshStatusView.text = "✓ Mesh packet copied to OFFGRID-${meshTransit.peerId.takeLast(6)} · safe to relay onward"
            }
            return
        }

        runOnUiThread {
            val index = directMessages.indexOfFirst { it.id == transportId }
            if (index >= 0) {
                directMessages[index] = directMessages[index].copy(delivered = true)
                chatStore.markDelivered(transportId)
                renderDirectChat()
            }
        }
    }

    private fun requestPeerConnection(peer: PeerInfo) {
        if (!hasBluetoothPermissions()) {
            setStatus("Bluetooth permission required")
            return
        }

        if (!chatManager.isSecure()) {
            connectNow(peer)
            return
        }

        val current = activePeerId
        val targetKnown = chatStore.peerForAddress(peer.address)?.peerId
        if (current != null && targetKnown == current) {
            setStatus("Already connected to OFFGRID-${current.takeLast(6)}")
            return
        }

        pendingSwitchPeer = peer
        switchGeneration += 1
        val generation = switchGeneration
        setStatus("Preparing safe relay handoff to next peer…")
        val controlId = chatManager.sendText(CONTROL_RELEASE)
        switchControlTransportId = controlId

        uiHandler.postDelayed({
            if (generation == switchGeneration && pendingSwitchPeer != null) {
                performPendingSwitch()
            }
        }, SWITCH_FALLBACK_MS)
    }

    private fun performPendingSwitch() {
        val target = pendingSwitchPeer ?: return
        pendingSwitchPeer = null
        switchControlTransportId = null
        switchGeneration += 1
        chatManager.disconnect()
        clearActiveSessionUi("Switching relay carrier…")
        uiHandler.postDelayed({ connectNow(target) }, SWITCH_CONNECT_DELAY_MS)
    }

    private fun connectNow(peer: PeerInfo) {
        activePeerId = null
        activePeerFingerprint = null
        activeSafetyCode = null
        activePeerVerified = false
        activeIdentityChanged = false
        meshTransportAcks.clear()
        meshInFlight.clear()

        val known = chatStore.peerForAddress(peer.address)
        chatPanel.visibility = View.VISIBLE
        composerBar.visibility = View.GONE
        verifyButton.visibility = View.GONE
        identityView.text = "Waiting for signed peer identity…"
        chatTitle.text = if (known != null) {
            "Connecting · OFFGRID-${known.peerId.takeLast(6)}"
        } else {
            "Connecting · OFFGRID-${peer.id.takeLast(6)}"
        }
        directMessages.clear()
        renderDirectChat()
        chatManager.connect(peer.device)
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

        directMessages.clear()
        directMessages += restored
        chatTitle.text = "Encrypted direct chat · OFFGRID-${peer.deviceId.takeLast(6)}"
        chatPanel.visibility = View.VISIBLE
        renderIdentityPanel()
        renderDirectChat()

        if (activeIdentityChanged) {
            composerBar.visibility = View.GONE
            sendButton.isEnabled = false
            setStatus("IDENTITY CHANGED · direct + mesh sync blocked")
        } else {
            composerBar.visibility = View.VISIBLE
            sendButton.isEnabled = true
            setStatus(if (activePeerVerified) "Secure session ready · verified peer" else "Secure session ready · verify identity when convenient")
        }
        renderPeers()
    }

    private fun clearActiveSessionUi(message: String) {
        activePeerId = null
        activePeerFingerprint = null
        activeSafetyCode = null
        activePeerVerified = false
        activeIdentityChanged = false
        composerBar.visibility = View.GONE
        sendButton.isEnabled = false
        chatTitle.text = "Encrypted direct chat · no active peer"
        identityView.text = message
        verifyButton.visibility = View.GONE
        setStatus(message)
        renderPeers()
    }

    private fun renderIdentityPanel() {
        val fingerprint = activePeerFingerprint?.take(16)?.chunked(4)?.joinToString("-") ?: "unknown"
        val safety = activeSafetyCode ?: "----"
        identityView.text = when {
            activeIdentityChanged -> "⚠ IDENTITY CHANGED\nPeer fingerprint: $fingerprint\nConnection blocked."
            activePeerVerified -> "✓ VERIFIED IDENTITY\nSafety code: $safety\nPeer fingerprint: $fingerprint"
            else -> "UNVERIFIED IDENTITY\nSafety code: $safety\nPeer fingerprint: $fingerprint\nCompare Safety Code on both phones, then verify if identical."
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
        }
    }

    private fun sendDirectMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isBlank() || activeIdentityChanged || !chatManager.isSecure()) return
        val peerId = activePeerId ?: return
        val id = chatManager.sendText(text) ?: return
        val createdAt = System.currentTimeMillis()
        directMessages += ChatEntry(id, true, text, false, createdAt)
        chatStore.saveMessage(ChatStore.StoredMessage(id, peerId, true, text, false, createdAt))
        messageInput.setText("")
        renderDirectChat()
    }

    private fun renderDirectChat() {
        chatLog.text = if (directMessages.isEmpty()) {
            "No direct messages in this peer history."
        } else {
            directMessages.joinToString("\n\n") { msg ->
                if (msg.mine) "You: ${msg.text}\n${if (msg.delivered) "✓ Delivered" else "… Sending"}"
                else "Peer: ${msg.text}"
            }
        }
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun renderMesh() {
        val config = runCatching { meshStore.currentGroup() }.getOrNull()
        meshSendButton.isEnabled = config != null
        if (config == null) {
            meshStatusView.text = "No mesh group yet."
            meshLog.text = "Mesh group messages appear here and survive peer switching."
            return
        }

        val stored = runCatching { meshStore.queueCount() }.getOrDefault(0)
        val readable = runCatching { meshStore.readableMessages() }.getOrDefault(emptyList())
        meshStatusView.text = "Group: ${config.name} · ID ${config.groupId}\nStored relay packets: $stored"
        meshLog.text = if (readable.isEmpty()) {
            "Group ready. Mesh messages will stay here even while direct BLE peers change."
        } else {
            readable.joinToString("\n\n") { msg ->
                val who = if (msg.mine) "You" else "OFFGRID-${msg.senderId.takeLast(6)}"
                val route = if (msg.mine) "origin · copied to ${msg.syncedPeers} peer(s)" else "received at hop ${msg.hopCount}"
                "$who: ${msg.text}\nMesh: $route"
            }
        }
        meshScroll.post { meshScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun meshSummary(prefix: String): String {
        val config = runCatching { meshStore.currentGroup() }.getOrNull() ?: return prefix
        return "$prefix\n${config.name} · ${config.groupId} · stored ${runCatching { meshStore.queueCount() }.getOrDefault(0)}"
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
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startDiscovery() else requestPermissions(missing.toTypedArray(), REQUEST_BLUETOOTH)
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
        val bt = adapter ?: run { setStatus("Bluetooth unavailable"); return }
        if (!bt.isEnabled) { setStatus("Turn Bluetooth on first"); return }
        if (!chatManager.startServer()) { setStatus("Could not start OFFGRID server"); return }
        val scanner = bt.bluetoothLeScanner ?: run { setStatus("BLE scanner unavailable"); return }

        running = true
        peers.clear()
        renderPeers()
        val filter = ScanFilter.Builder().setServiceUuid(serviceUuid).build()
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), scanSettings, scanCallback)

        if (bt.isMultipleAdvertisementSupported) {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .build()
            val data = AdvertiseData.Builder().addServiceUuid(serviceUuid).setIncludeDeviceName(false).build()
            bt.bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        }
        setStatus("Discovery active")
    }

    @SuppressLint("MissingPermission")
    private fun stopDiscovery() {
        if (!running) return
        if (hasBluetoothPermissions()) {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        }
        running = false
        setStatus(if (chatManager.isSecure()) "Discovery stopped · current secure link remains" else "Discovery stopped")
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
                .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
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
                text = "No OFFGRID nodes detected yet."
                textSize = 15f
            })
            return
        }

        peers.values.sortedByDescending { it.rssi }.forEach { peer ->
            val known = chatStore.peerForAddress(peer.address)
            peersContainer.addView(Button(this).apply {
                text = when {
                    known?.verified == true -> "OFFGRID-${known.peerId.takeLast(6)} · ${peer.rssi} dBm\n✓ VERIFIED · TAP TO CONNECT / RELAY"
                    known != null -> "OFFGRID-${known.peerId.takeLast(6)} · ${peer.rssi} dBm\nKNOWN PEER · TAP TO CONNECT / RELAY"
                    else -> "OFFGRID-${peer.id.takeLast(6)} · ${peer.rssi} dBm\nTAP TO CONNECT / RELAY"
                }
                isAllCaps = false
                setOnClickListener { requestPeerConnection(peer) }
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
        private const val CONTROL_RELEASE = "@OGCTL1|RELEASE_FOR_RELAY"
        private const val CONTROL_RELEASE_DELAY_MS = 350L
        private const val SWITCH_FALLBACK_MS = 1_200L
        private const val SWITCH_CONNECT_DELAY_MS = 450L
    }
}
