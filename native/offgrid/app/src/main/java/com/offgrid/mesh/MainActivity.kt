package com.offgrid.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
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
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

class MainActivity : Activity() {

    private val serviceUuid = ParcelUuid(UUID.fromString("9f92b6a8-d601-4db8-a2fc-0ff67f0a6b71"))
    private lateinit var statusView: TextView
    private lateinit var peersView: TextView
    private lateinit var myIdView: TextView
    private var running = false

    private val peers = linkedMapOf<String, PeerInfo>()

    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            runOnUiThread { setStatus("BLE advertising + scanning") }
        }

        override fun onStartFailure(errorCode: Int) {
            runOnUiThread { setStatus("Scanning active · advertise error $errorCode") }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val id = runCatching {
                result.device.address.replace(":", "").takeLast(12)
            }.getOrElse {
                "NODE${result.hashCode().toUInt().toString(16)}"
            }

            if (id.isBlank()) return
            peers[id] = PeerInfo(id, result.rssi, System.currentTimeMillis())
            pruneAndRender()
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread { setStatus("Scan failed: $errorCode") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        myIdView.text = "This device: ${deviceId()}"
    }

    override fun onDestroy() {
        stopDiscovery()
        super.onDestroy()
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "OFFGRID"
            textSize = 34f
        })
        root.addView(TextView(this).apply {
            text = "Phase 0 · Native BLE discovery"
            textSize = 16f
        })

        myIdView = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(20), 0, 0)
        }
        root.addView(myIdView)

        statusView = TextView(this).apply {
            text = "Status: Idle"
            textSize = 15f
            setPadding(0, dp(8), 0, dp(16))
        }
        root.addView(statusView)

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

        root.addView(buttonRow)

        root.addView(TextView(this).apply {
            text = "Nearby OFFGRID nodes"
            textSize = 20f
            setPadding(0, dp(24), 0, dp(8))
        })

        peersView = TextView(this).apply {
            text = "No OFFGRID nodes detected yet.\n\nInstall this APK on another Android phone, turn Bluetooth on, then tap START DISCOVERY on both phones."
            textSize = 16f
        }

        val scroll = ScrollView(this)
        scroll.addView(peersView)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
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
    private fun stopDiscovery() {
        if (!running) return
        if (hasBluetoothPermissions()) {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        }
        running = false
        setStatus("Discovery stopped")
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
        if (peers.isEmpty()) {
            peersView.text = "No OFFGRID nodes detected yet.\n\nInstall this APK on another Android phone, turn Bluetooth on, then tap START DISCOVERY on both phones."
            return
        }

        peersView.text = peers.values
            .sortedByDescending { it.rssi }
            .joinToString("\n\n") { peer ->
                "OFFGRID-${peer.id.takeLast(6)}\nRSSI ${peer.rssi} dBm\nNode ${peer.id}"
            }
    }

    private fun setStatus(value: String) {
        statusView.text = "Status: $value"
    }

    private data class PeerInfo(val id: String, val rssi: Int, val lastSeen: Long)

    companion object {
        private const val REQUEST_BLUETOOTH = 1001
        private const val PEER_TIMEOUT_MS = 20_000L
    }
}
