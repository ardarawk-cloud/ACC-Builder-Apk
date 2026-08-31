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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MainActivity : Activity() {

    private val serviceUuid = ParcelUuid(BleChatManager.SERVICE_UUID)
    private val uiHandler = Handler(Looper.getMainLooper())

    private lateinit var statusView: TextView
    private lateinit var navButtons: List<Button>
    private lateinit var panels: List<View>

    private lateinit var chatPeerTitle: TextView
    private lateinit var chatSecurity: TextView
    private lateinit var verifyButton: Button
    private lateinit var chatLog: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button

    private lateinit var groupHeader: TextView
    private lateinit var groupSetupContainer: LinearLayout
    private lateinit var meshNameInput: EditText
    private lateinit var meshCodeInput: EditText
    private lateinit var meshJoinButton: Button
    private lateinit var meshStatusView: TextView
    private lateinit var meshLog: TextView
    private lateinit var meshScroll: ScrollView
    private lateinit var meshMessageInput: EditText
    private lateinit var meshSendButton: Button

    private lateinit var nearbySummary: TextView
    private lateinit var peersContainer: LinearLayout

    private lateinit var settingsInfo: TextView
    private lateinit var autoRelayButton: Button
    private lateinit var diagnosticsView: TextView

    private lateinit var chatManager: BleChatManager
    private lateinit var chatStore: ChatStore
    private lateinit var meshStore: MeshStore

    private var running = false
    private var currentTab = TAB_CHATS
    @Volatile private var activePeerId: String? = null
    private var activeAddress: String? = null
    private var connectingAddress: String? = null
    private var displayedPeerId: String? = null
    private var activePeerFingerprint: String? = null
    private var activeSafetyCode: String? = null
    private var activePeerVerified = false
    @Volatile private var activeIdentityChanged = false
    private var connectedAt = 0L

    private var currentConnectionAuto = false
    private var manualLockUntil = 0L
    private var switching = false
    private var pendingSwitch: PeerInfo? = null
    private val relayAttemptedAt = mutableMapOf<String, Long>()

    private val peers = linkedMapOf<String, PeerInfo>()
    private val messages = mutableListOf<ChatEntry>()
    private val meshTransportAcks = ConcurrentHashMap<String, MeshTransit>()
    private val meshInFlight = ConcurrentHashMap.newKeySet<String>()
    private val localId by lazy { deviceId() }

    private val appPrefs by lazy { getSharedPreferences("offgrid_alpha", Context.MODE_PRIVATE) }
    private var autoRelayEnabled: Boolean
        get() = appPrefs.getBoolean("auto_relay", true)
        set(value) { appPrefs.edit().putBoolean("auto_relay", value).apply() }

    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            runOnUiThread { setStatus("Nearby network active") }
        }

        override fun onStartFailure(errorCode: Int) {
            runOnUiThread { setStatus("Scanning active · broadcast unavailable ($errorCode)") }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = runCatching { result.device.address }.getOrElse { return }
            val fallbackId = address.replace(":", "").takeLast(12)
            if (fallbackId.isBlank()) return
            peers[address] = PeerInfo(
                id = fallbackId,
                address = address,
                device = result.device,
                rssi = result.rssi,
                lastSeen = System.currentTimeMillis()
            )
            pruneAndRender()
            scheduleRelayPump(250)
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread { setStatus("Nearby scan failed ($errorCode)") }
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
            onState = { state -> runOnUiThread { setStatus(state); refreshDiagnostics() } },
            onSecurePeer = { peer -> handleSecurePeer(peer) },
            onIncomingMessage = { id, text -> handleIncomingTransportMessage(id, text) },
            onDelivered = { id -> handleTransportDelivered(id) },
            onDisconnected = { runOnUiThread { handleDisconnected() } },
            onPeerRelease = { runOnUiThread { setStatus("Peer released link for relay handoff") } }
        )

        meshStore.currentGroup()?.let {
            meshNameInput.setText(it.name)
            setGroupSetupExpanded(false)
        } ?: setGroupSetupExpanded(true)

        renderMesh()
        renderPeers()
        refreshSettings()
        showTab(TAB_CHATS)
        scheduleRelayPump(RELAY_PUMP_MS)
        uiHandler.postDelayed({ requestPermissionsAndStart() }, 350)
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(8))
        }

        root.addView(TextView(this).apply {
            text = "OFFGRID"
            textSize = 30f
        })
        root.addView(TextView(this).apply {
            text = "Alpha v1 · Offline phone mesh"
            textSize = 13f
        })
        statusView = TextView(this).apply {
            text = "Status: Starting"
            textSize = 14f
            setPadding(0, dp(6), 0, dp(8))
        }
        root.addView(statusView)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val chats = navButton("CHATS", TAB_CHATS)
        val groups = navButton("GROUPS", TAB_GROUPS)
        val nearby = navButton("NEARBY", TAB_NEARBY)
        val settings = navButton("SETTINGS", TAB_SETTINGS)
        navButtons = listOf(chats, groups, nearby, settings)
        navButtons.forEach { button ->
            nav.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(nav)

        val content = FrameLayout(this)
        val chatPanel = buildChatPanel(::dp)
        val groupPanel = buildGroupPanel(::dp)
        val nearbyPanel = buildNearbyPanel(::dp)
        val settingsPanel = buildSettingsPanel(::dp)
        panels = listOf(chatPanel, groupPanel, nearbyPanel, settingsPanel)
        panels.forEach { panel ->
            content.addView(panel, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun navButton(label: String, tab: Int): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 11f
        setOnClickListener { showTab(tab) }
    }

    private fun buildChatPanel(dp: (Int) -> Int): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        chatPeerTitle = TextView(this).apply {
            text = "No active chat"
            textSize = 20f
        }
        panel.addView(chatPeerTitle)
        chatSecurity = TextView(this).apply {
            text = "Connect to a nearby OFFGRID phone."
            textSize = 13f
            setPadding(0, dp(4), 0, dp(4))
        }
        panel.addView(chatSecurity)
        verifyButton = Button(this).apply {
            text = "VERIFY IDENTITY"
            isAllCaps = false
            visibility = View.GONE
            setOnClickListener { verifyCurrentPeer() }
        }
        panel.addView(verifyButton)

        chatLog = TextView(this).apply {
            text = "Your encrypted direct messages appear here."
            textSize = 15f
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        chatScroll = ScrollView(this).apply { addView(chatLog) }
        panel.addView(chatScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, 0)
        }
        messageInput = EditText(this).apply {
            hint = "Offline message"
            maxLines = 3
            minLines = 1
        }
        composer.addView(messageInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sendButton = Button(this).apply {
            text = "SEND"
            isEnabled = false
            setOnClickListener { sendCurrentMessage() }
        }
        composer.addView(sendButton)
        panel.addView(composer)
        return panel
    }

    private fun buildGroupPanel(dp: (Int) -> Int): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        groupHeader = TextView(this).apply {
            text = "No mesh group"
            textSize = 20f
        }
        panel.addView(groupHeader)

        groupSetupContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(5), 0, dp(5))
        }
        meshNameInput = EditText(this).apply {
            hint = "Group name"
            maxLines = 1
        }
        meshCodeInput = EditText(this).apply {
            hint = "Shared group code · 6+ characters"
            maxLines = 1
        }
        groupSetupContainer.addView(meshNameInput)
        groupSetupContainer.addView(meshCodeInput)
        panel.addView(groupSetupContainer)

        meshJoinButton = Button(this).apply {
            text = "CREATE / JOIN GROUP"
            isAllCaps = false
            setOnClickListener {
                if (groupSetupContainer.visibility != View.VISIBLE) setGroupSetupExpanded(true)
                else joinMeshGroup()
            }
        }
        panel.addView(meshJoinButton)

        meshStatusView = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(4), 0, dp(4))
        }
        panel.addView(meshStatusView)

        meshLog = TextView(this).apply {
            textSize = 15f
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        meshScroll = ScrollView(this).apply { addView(meshLog) }
        panel.addView(meshScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, 0)
        }
        meshMessageInput = EditText(this).apply {
            hint = "Message the group"
            maxLines = 3
            minLines = 1
        }
        composer.addView(meshMessageInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        meshSendButton = Button(this).apply {
            text = "SEND"
            isEnabled = false
            setOnClickListener { sendMeshMessage() }
        }
        composer.addView(meshSendButton)
        panel.addView(composer)
        return panel
    }

    private fun buildNearbyPanel(dp: (Int) -> Int): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        panel.addView(TextView(this).apply {
            text = "Nearby OFFGRID"
            textSize = 20f
        })
        nearbySummary = TextView(this).apply {
            text = "Searching for phones nearby…"
            textSize = 13f
            setPadding(0, dp(4), 0, dp(5))
        }
        panel.addView(nearbySummary)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "START"
            setOnClickListener { requestPermissionsAndStart() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "STOP"
            setOnClickListener { stopDiscovery() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(row)

        peersContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(peersContainer) }
        panel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return panel
    }

    private fun buildSettingsPanel(dp: (Int) -> Int): View {
        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        scroll.addView(panel)
        panel.addView(TextView(this).apply {
            text = "OFFGRID Settings"
            textSize = 20f
        })
        settingsInfo = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(8), 0, dp(8))
        }
        panel.addView(settingsInfo)

        autoRelayButton = Button(this).apply {
            isAllCaps = false
            setOnClickListener {
                autoRelayEnabled = !autoRelayEnabled
                refreshSettings()
                renderMesh()
                scheduleRelayPump(100)
            }
        }
        panel.addView(autoRelayButton)

        panel.addView(Button(this).apply {
            text = "SHARE OFFGRID APP OFFLINE"
            isAllCaps = false
            setOnClickListener { shareCurrentApk() }
        })
        panel.addView(TextView(this).apply {
            text = "Uses Android's offline share options such as Nearby/Quick Share or Bluetooth when available. Installation still requires approval on the receiving phone."
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        })

        panel.addView(Button(this).apply {
            text = "COPY DIAGNOSTICS"
            isAllCaps = false
            setOnClickListener { copyDiagnostics() }
        })
        diagnosticsView = TextView(this).apply {
            textSize = 12f
            setPadding(0, dp(8), 0, 0)
        }
        panel.addView(diagnosticsView)
        return scroll
    }

    private fun showTab(tab: Int) {
        currentTab = tab.coerceIn(0, panels.lastIndex)
        panels.forEachIndexed { index, view -> view.visibility = if (index == currentTab) View.VISIBLE else View.GONE }
        navButtons.forEachIndexed { index, button -> button.isEnabled = index != currentTab }
        if (currentTab == TAB_GROUPS) renderMesh()
        if (currentTab == TAB_NEARBY) renderPeers()
        if (currentTab == TAB_SETTINGS) refreshSettings()
    }

    private fun setGroupSetupExpanded(expanded: Boolean) {
        groupSetupContainer.visibility = if (expanded) View.VISIBLE else View.GONE
        meshJoinButton.text = if (expanded) {
            if (meshStore.currentGroup() == null) "CREATE / JOIN GROUP" else "SAVE GROUP"
        } else "CHANGE GROUP"
    }

    private fun joinMeshGroup() {
        val name = meshNameInput.text.toString().trim()
        val code = meshCodeInput.text.toString().trim()
        if (name.isBlank() || code.length < 6) {
            meshStatusView.text = "Enter a group name and a code of at least 6 characters."
            return
        }
        meshJoinButton.isEnabled = false
        meshStatusView.text = "Securing group…"
        Thread {
            val config = runCatching { meshStore.configureGroup(name, code) }.getOrNull()
            runOnUiThread {
                meshJoinButton.isEnabled = true
                if (config == null) {
                    meshStatusView.text = "Could not save group."
                } else {
                    meshCodeInput.setText("")
                    setGroupSetupExpanded(false)
                    renderMesh()
                    meshStatusView.text = "Group ready · relay works automatically while OFFGRID is open."
                    scheduleRelayPump(100)
                }
            }
        }.start()
    }

    private fun sendMeshMessage() {
        val text = meshMessageInput.text.toString().trim()
        if (text.isBlank()) return
        val envelope = runCatching { meshStore.createMessage(text) }.getOrNull()
        if (envelope == null) {
            meshStatusView.text = "Create or join a group first."
            return
        }
        meshMessageInput.setText("")
        renderMesh()
        val peerId = activePeerId
        if (peerId != null && chatManager.isSecure() && !activeIdentityChanged) syncMeshWithPeer(peerId)
        else meshStatusView.text = "Queued offline · OFFGRID will relay it when another node is reached."
        scheduleRelayPump(100)
    }

    private fun handleSecurePeer(peer: BleChatManager.SecurePeer): Boolean {
        val check = chatStore.observePeerIdentity(
            peerId = peer.deviceId,
            address = peer.address.ifBlank { null },
            fingerprint = peer.identityFingerprint
        )
        val accepted = check.state != ChatStore.IdentityState.CHANGED
        if (!accepted) {
            runOnUiThread {
                activeIdentityChanged = true
                setStatus("Identity changed · connection blocked")
            }
            return false
        }
        val restored = chatStore.loadMessages(peer.deviceId).map {
            ChatEntry(it.id, it.mine, it.text, it.delivered, it.createdAt)
        }
        runOnUiThread {
            activePeerId = peer.deviceId
            activeAddress = peer.address.ifBlank { connectingAddress }
            connectingAddress = null
            displayedPeerId = peer.deviceId
            activePeerFingerprint = peer.identityFingerprint
            activeSafetyCode = peer.safetyCode
            activePeerVerified = check.verified
            activeIdentityChanged = false
            connectedAt = System.currentTimeMillis()
            switching = false
            pendingSwitch = null
            messages.clear()
            messages += restored
            renderDirectChat()
            renderPeers()
            syncMeshWithPeer(peer.deviceId)
            setStatus("Connected securely · OFFGRID-${peer.deviceId.takeLast(6)}")
            scheduleRelayPump(900)
        }
        return true
    }

    private fun handleDisconnected() {
        activePeerId = null
        activeAddress = null
        connectingAddress = null
        meshTransportAcks.clear()
        meshInFlight.clear()
        sendButton.isEnabled = false
        if (displayedPeerId != null) chatPeerTitle.text = "OFFGRID-${displayedPeerId!!.takeLast(6)} · offline"
        if (pendingSwitch == null) switching = false
        renderPeers()
        refreshDiagnostics()
        scheduleRelayPump(500)
    }

    private fun sendCurrentMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isBlank()) return
        val peerId = activePeerId
        if (peerId == null || !chatManager.isSecure() || activeIdentityChanged) {
            setStatus("Direct peer is not connected")
            return
        }
        manualLockUntil = System.currentTimeMillis() + MANUAL_CHAT_LOCK_MS
        currentConnectionAuto = false
        val id = chatManager.sendText(text) ?: return
        val now = System.currentTimeMillis()
        messages += ChatEntry(id, true, text, false, now)
        chatStore.saveMessage(ChatStore.StoredMessage(id, peerId, true, text, false, now))
        messageInput.setText("")
        renderDirectChat()
    }

    private fun handleIncomingTransportMessage(transportId: String, text: String) {
        val envelope = parseMeshWire(text)
        if (envelope != null) {
            val fromPeer = activePeerId ?: return
            val result = runCatching { meshStore.receiveEnvelope(envelope, fromPeer) }.getOrNull() ?: return
            runOnUiThread {
                renderMesh()
                meshStatusView.text = when {
                    !result.isNew -> "Duplicate packet ignored safely."
                    result.readable != null -> "Message received · relay copy stored."
                    else -> "Sealed relay packet stored for another group."
                }
                scheduleRelayPump(250)
            }
            return
        }

        runOnUiThread {
            if (activeIdentityChanged) return@runOnUiThread
            val peerId = activePeerId ?: return@runOnUiThread
            if (messages.none { it.id == transportId }) {
                val now = System.currentTimeMillis()
                messages += ChatEntry(transportId, false, text, true, now)
                chatStore.saveMessage(ChatStore.StoredMessage(transportId, peerId, false, text, true, now))
                renderDirectChat()
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
                scheduleRelayPump(350)
            }
            return
        }
        runOnUiThread {
            val index = messages.indexOfFirst { it.id == transportId }
            if (index >= 0) {
                messages[index] = messages[index].copy(delivered = true)
                chatStore.markDelivered(transportId)
                renderDirectChat()
            }
        }
    }

    private fun syncMeshWithPeer(peerId: String) {
        if (!chatManager.isSecure() || activeIdentityChanged || activePeerId != peerId) return
        val pending = runCatching { meshStore.pendingForPeer(peerId, 12) }.getOrDefault(emptyList())
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
        if (sent > 0) meshStatusView.text = "Relaying $sent packet(s) through OFFGRID-${peerId.takeLast(6)}…"
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

    private fun renderDirectChat() {
        val peerId = displayedPeerId
        if (peerId == null) {
            chatPeerTitle.text = "No active chat"
            chatSecurity.text = "Open NEARBY and tap a phone to start an encrypted direct chat."
            verifyButton.visibility = View.GONE
            sendButton.isEnabled = false
        } else {
            val connected = activePeerId == peerId && chatManager.isSecure()
            chatPeerTitle.text = "OFFGRID-${peerId.takeLast(6)}${if (connected) " · connected" else " · offline"}"
            val fp = activePeerFingerprint?.take(16)?.chunked(4)?.joinToString("-") ?: "unknown"
            chatSecurity.text = when {
                activeIdentityChanged -> "⚠ Identity changed · blocked"
                activePeerVerified -> "✓ Verified identity · ${activeSafetyCode ?: ""}"
                connected -> "Unverified identity · Safety code ${activeSafetyCode ?: "----"} · Fingerprint $fp"
                else -> "Encrypted history stored locally"
            }
            verifyButton.visibility = if (connected && !activePeerVerified && !activeIdentityChanged) View.VISIBLE else View.GONE
            sendButton.isEnabled = connected && !activeIdentityChanged
        }

        chatLog.text = if (messages.isEmpty()) {
            "No messages yet."
        } else {
            messages.joinToString("\n\n") { msg ->
                if (msg.mine) "You: ${msg.text}\n${if (msg.delivered) "✓ Delivered" else "… Sending"}"
                else "Peer: ${msg.text}"
            }
        }
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun verifyCurrentPeer() {
        if (activeIdentityChanged) return
        val peerId = activePeerId ?: return
        val fingerprint = activePeerFingerprint ?: return
        if (chatStore.markPeerVerified(peerId, fingerprint)) {
            activePeerVerified = true
            renderDirectChat()
            renderPeers()
            setStatus("Identity verified")
        }
    }

    private fun renderMesh() {
        val config = runCatching { meshStore.currentGroup() }.getOrNull()
        meshSendButton.isEnabled = config != null
        if (config == null) {
            groupHeader.text = "No mesh group"
            meshStatusView.text = "Create the same group name + code on the phones that should read the group chat."
            meshLog.text = "Group messages will be queued and carried through nearby OFFGRID phones."
            setGroupSetupExpanded(true)
            return
        }

        groupHeader.text = config.name
        val stored = runCatching { meshStore.queueCount() }.getOrDefault(0)
        val readable = runCatching { meshStore.readableMessages() }.getOrDefault(emptyList())
        meshStatusView.text = "${if (autoRelayEnabled) "Auto relay ON" else "Auto relay OFF"} · $stored stored packet(s)"
        meshLog.text = if (readable.isEmpty()) {
            "Group ready. Messages can travel phone → phone without internet."
        } else {
            readable.joinToString("\n\n") { msg ->
                val who = if (msg.mine) "You" else "OFFGRID-${msg.senderId.takeLast(6)}"
                val route = if (msg.mine) {
                    when {
                        msg.syncedPeers == 0 -> "Queued"
                        msg.syncedPeers == 1 -> "Relayed to 1 node"
                        else -> "Relayed to ${msg.syncedPeers} nodes"
                    }
                } else "Received via ${msg.hopCount} hop(s)"
                "$who: ${msg.text}\n$route"
            }
        }
        meshScroll.post { meshScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun requestPermissionsAndStart() {
        if (!::chatManager.isInitialized) return
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startDiscovery()
        else requestPermissions(missing.toTypedArray(), REQUEST_BLUETOOTH)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startDiscovery()
            else setStatus("Bluetooth permission is required for offline networking")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        if (running) return
        val bluetoothAdapter = adapter ?: run {
            setStatus("Bluetooth is unavailable")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            setStatus("Turn Bluetooth on")
            return
        }
        if (!chatManager.startServer()) {
            setStatus("Could not start OFFGRID receiver")
            return
        }
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            setStatus("BLE scanner unavailable")
            return
        }
        running = true
        peers.clear()
        renderPeers()

        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(serviceUuid).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback
        )

        if (bluetoothAdapter.isMultipleAdvertisementSupported) {
            bluetoothAdapter.bluetoothLeAdvertiser?.startAdvertising(
                AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(true)
                    .build(),
                AdvertiseData.Builder()
                    .addServiceUuid(serviceUuid)
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .build(),
                advertiseCallback
            )
        }
        setStatus("Nearby network active")
        scheduleRelayPump(500)
    }

    @SuppressLint("MissingPermission")
    private fun stopDiscovery() {
        if (!running) return
        if (hasBluetoothPermissions()) {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        }
        running = false
        setStatus(if (chatManager.isSecure()) "Nearby scan stopped · chat remains connected" else "Nearby network stopped")
        renderPeers()
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ).all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        } else checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun pruneAndRender() {
        val cutoff = System.currentTimeMillis() - PEER_TIMEOUT_MS
        peers.entries.removeIf { it.value.lastSeen < cutoff }
        runOnUiThread { renderPeers() }
    }

    private fun renderPeers() {
        peersContainer.removeAllViews()
        nearbySummary.text = when {
            !running -> "Nearby network is stopped."
            peers.isEmpty() -> "Searching… no OFFGRID phones visible yet."
            else -> "${peers.size} OFFGRID phone(s) visible · tap one for direct chat. Auto relay can use them in the background."
        }
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        if (peers.isEmpty()) return

        peers.values.sortedByDescending { it.rssi }.forEach { peer ->
            val known = chatStore.peerForAddress(peer.address)
            val knownId = known?.peerId ?: peer.id
            val connected = activeAddress == peer.address && chatManager.isSecure()
            peersContainer.addView(Button(this).apply {
                text = buildString {
                    append("OFFGRID-${knownId.takeLast(6)} · ${signalLabel(peer.rssi)}")
                    if (connected) append("\nCONNECTED")
                    else if (known?.verified == true) append("\n✓ VERIFIED · TAP TO CHAT")
                    else append("\nTAP TO CHAT")
                }
                isAllCaps = false
                setOnClickListener {
                    manualLockUntil = System.currentTimeMillis() + MANUAL_CHAT_LOCK_MS
                    showTab(TAB_CHATS)
                    switchToPeer(peer, automatic = false)
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            })
        }
    }

    private fun signalLabel(rssi: Int): String = when {
        rssi >= -60 -> "Strong"
        rssi >= -75 -> "Good"
        rssi >= -88 -> "Weak"
        else -> "Very weak"
    }

    private fun switchToPeer(peer: PeerInfo, automatic: Boolean) {
        if (!hasBluetoothPermissions()) return
        if (activeAddress == peer.address && chatManager.isSecure()) return
        if (switching && pendingSwitch?.address == peer.address) return

        if (chatManager.isSecure()) {
            switching = true
            pendingSwitch = peer
            currentConnectionAuto = automatic
            setStatus(if (automatic) "Relaying to another nearby node" else "Switching chat")
            chatManager.releaseForHandoff()
            uiHandler.postDelayed({
                val target = pendingSwitch ?: return@postDelayed
                pendingSwitch = null
                startConnection(target, automatic)
            }, HANDOFF_WAIT_MS)
        } else {
            startConnection(peer, automatic)
        }
    }

    private fun startConnection(peer: PeerInfo, automatic: Boolean) {
        connectingAddress = peer.address
        currentConnectionAuto = automatic
        relayAttemptedAt[peer.address] = System.currentTimeMillis()
        switching = true
        if (!automatic) {
            displayedPeerId = chatStore.peerIdForAddress(peer.address) ?: peer.id
            messages.clear()
            chatStore.peerIdForAddress(peer.address)?.let { knownId ->
                messages += chatStore.loadMessages(knownId).map {
                    ChatEntry(it.id, it.mine, it.text, it.delivered, it.createdAt)
                }
            }
            renderDirectChat()
        }
        chatManager.connect(peer.device, autoReconnect = !automatic)
    }

    private fun scheduleRelayPump(delay: Long = RELAY_PUMP_MS) {
        uiHandler.removeCallbacks(relayRunnable)
        uiHandler.postDelayed(relayRunnable, delay)
    }

    private val relayRunnable = Runnable {
        autoRelayPump()
        scheduleRelayPump(RELAY_PUMP_MS)
    }

    private fun autoRelayPump() {
        if (!autoRelayEnabled || !running || meshStore.currentGroup() == null) return
        if (meshStore.queueCount() <= 0) return
        val now = System.currentTimeMillis()
        if (switching) return

        val peerId = activePeerId
        if (chatManager.isSecure() && peerId != null) {
            syncMeshWithPeer(peerId)
            val stillPending = meshStore.pendingForPeer(peerId, 1).isNotEmpty() ||
                meshInFlight.any { it.startsWith("$peerId|") }
            if (stillPending) return

            val canHandoff = currentConnectionAuto || (now > manualLockUntil && now - connectedAt > RELAY_DWELL_MS)
            if (!canHandoff) return
            val next = chooseRelayCandidate(excludeAddress = activeAddress)
            if (next != null) switchToPeer(next, automatic = true)
            else if (currentConnectionAuto) chatManager.releaseForHandoff()
            return
        }

        if (connectingAddress != null) return
        chooseRelayCandidate(excludeAddress = null)?.let { switchToPeer(it, automatic = true) }
    }

    private fun chooseRelayCandidate(excludeAddress: String?): PeerInfo? {
        val now = System.currentTimeMillis()
        return peers.values
            .asSequence()
            .filter { it.address != excludeAddress }
            .filter { now - it.lastSeen < PEER_TIMEOUT_MS }
            .filter { peer ->
                val knownId = chatStore.peerIdForAddress(peer.address)
                knownId == null || meshStore.pendingForPeer(knownId, 1).isNotEmpty()
            }
            .filter { now - (relayAttemptedAt[it.address] ?: 0L) > RELAY_RETRY_GAP_MS }
            .sortedWith(compareByDescending<PeerInfo> { it.rssi }.thenBy { relayAttemptedAt[it.address] ?: 0L })
            .firstOrNull()
    }

    private fun shareCurrentApk() {
        setStatus("Preparing OFFGRID installer for offline sharing…")
        Thread {
            runCatching {
                val dir = File(cacheDir, "shared").apply { mkdirs() }
                val target = File(dir, SHARED_APK_NAME)
                File(applicationInfo.sourceDir).inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                val uri = Uri.parse("content://${packageName}.selfapk/offgrid.apk")
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("OFFGRID Alpha v1", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread {
                    setStatus("Choose an offline sharing method")
                    startActivity(Intent.createChooser(intent, "Share OFFGRID App"))
                }
            }.onFailure { error ->
                runOnUiThread { setStatus("Could not prepare installer: ${error.javaClass.simpleName}") }
            }
        }.start()
    }

    private fun refreshSettings() {
        val group = runCatching { meshStore.currentGroup() }.getOrNull()
        settingsInfo.text = buildString {
            append("Version: ${BuildConfig.VERSION_NAME}\n")
            append("Device: OFFGRID-${localId.takeLast(6)}\n")
            append("Identity: ${chatManager.localIdentityFingerprint()}\n")
            append("Group: ${group?.name ?: "None"}\n")
            append("Internet required for chat: No")
        }
        autoRelayButton.text = "AUTO RELAY: ${if (autoRelayEnabled) "ON" else "OFF"}"
        refreshDiagnostics()
    }

    private fun diagnosticsText(): String = buildString {
        append("OFFGRID ${BuildConfig.VERSION_NAME}\n")
        append("device=$localId\n")
        append("identity=${chatManager.localIdentityFingerprint()}\n")
        append("nearby=${peers.size}\n")
        append("discovery=$running\n")
        append("secure=${chatManager.isSecure()}\n")
        append("peer=${activePeerId ?: "none"}\n")
        append("autoRelay=$autoRelayEnabled\n")
        append("meshGroup=${meshStore.currentGroup()?.groupId ?: "none"}\n")
        append("meshQueue=${runCatching { meshStore.queueCount() }.getOrDefault(-1)}\n")
        append("meshInFlight=${meshInFlight.size}")
    }

    private fun refreshDiagnostics() {
        if (::diagnosticsView.isInitialized) diagnosticsView.text = diagnosticsText()
    }

    private fun copyDiagnostics() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("OFFGRID diagnostics", diagnosticsText()))
        setStatus("Diagnostics copied")
    }

    private fun setStatus(value: String) {
        statusView.text = "Status: $value"
    }

    private fun deviceId(): String {
        val prefs = getSharedPreferences("offgrid_identity", Context.MODE_PRIVATE)
        val existing = prefs.getString("device_id", null)
        if (existing != null) return existing.replace("-", "").take(12)
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", created).apply()
        return created.replace("-", "").take(12)
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
        private const val TAB_CHATS = 0
        private const val TAB_GROUPS = 1
        private const val TAB_NEARBY = 2
        private const val TAB_SETTINGS = 3
        private const val REQUEST_BLUETOOTH = 1001
        private const val PEER_TIMEOUT_MS = 20_000L
        private const val MESH_WIRE = "@OGM1"
        private const val RELAY_PUMP_MS = 2_500L
        private const val RELAY_DWELL_MS = 4_000L
        private const val RELAY_RETRY_GAP_MS = 12_000L
        private const val HANDOFF_WAIT_MS = 850L
        private const val MANUAL_CHAT_LOCK_MS = 90_000L
        private const val SHARED_APK_NAME = "OFFGRID-Alpha-v1.apk"
    }
}
