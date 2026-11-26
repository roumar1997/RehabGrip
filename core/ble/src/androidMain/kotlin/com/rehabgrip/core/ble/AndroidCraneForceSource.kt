package com.rehabgrip.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real WH-C06 on Android: passive low-latency scan filtered by company ID 0x0100.
 * The scale never connects; every advertisement carries the current weight.
 * A watchdog restarts the scan when the stream dies (Android silently ends long
 * scans), never more often than every 30 s to stay under the 5-starts/30 s throttle.
 * Caller must hold BLUETOOTH_SCAN (API 31+) or location (API < 31).
 */
@SuppressLint("MissingPermission")
class AndroidCraneForceSource(context: Context) : ForceSource {
    override val name = "Crane Scale WH-C06"
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val _status = MutableStateFlow(ScaleStatus.IDLE)
    override val status: StateFlow<ScaleStatus> = _status.asStateFlow()
    private val _readings = MutableSharedFlow<ScaleReading>(extraBufferCapacity = 64)
    override val readings: SharedFlow<ScaleReading> = _readings.asSharedFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watchdog: Job? = null
    @Volatile private var lastPacketAt = 0L
    private var lastScanStartAt = 0L
    private var pipeline = ScalePipeline()

    override fun configureLock(lockedId: String?, onSelected: (String, String?) -> Unit) {
        pipeline = ScalePipeline(DeviceLock(lockedId, onSelected = onSelected))
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val payload = result.scanRecord?.getManufacturerSpecificData(CraneAdvertisementParser.COMPANY_ID) ?: return
            val name = result.scanRecord?.deviceName
            val reading = pipeline.process(result.device.address, name, result.rssi, payload, SystemClock.elapsedRealtime()) ?: return
            lastPacketAt = SystemClock.elapsedRealtime()
            _status.value = ScaleStatus.RECEIVING
            _readings.tryEmit(reading)
        }

        override fun onScanFailed(errorCode: Int) {
            _status.value = ScaleStatus.LOST
        }
    }

    override fun start() {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            _status.value = ScaleStatus.BLUETOOTH_OFF
            return
        }
        try {
            startScan()
        } catch (e: SecurityException) {
            _status.value = ScaleStatus.UNAUTHORIZED
            return
        }
        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                val now = SystemClock.elapsedRealtime()
                val silent = now - lastPacketAt
                if (_status.value == ScaleStatus.RECEIVING && silent > LOST_AFTER_MS) _status.value = ScaleStatus.LOST
                if (silent > RESCAN_AFTER_MS && now - lastScanStartAt > MIN_RESCAN_GAP_MS) {
                    runCatching { adapter.bluetoothLeScanner?.stopScan(callback) }
                    runCatching { startScan() }
                }
            }
        }
    }

    private fun startScan() {
        val filter = ScanFilter.Builder()
            .setManufacturerData(CraneAdvertisementParser.COMPANY_ID, ByteArray(0))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()
        adapter?.bluetoothLeScanner?.startScan(listOf(filter), settings, callback)
        lastScanStartAt = SystemClock.elapsedRealtime()
        if (_status.value != ScaleStatus.RECEIVING) _status.value = ScaleStatus.SCANNING
    }

    override fun stop() {
        watchdog?.cancel()
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        _status.value = ScaleStatus.IDLE
    }

    private companion object {
        const val HEARTBEAT_MS = 2_000L
        const val LOST_AFTER_MS = 4_000L
        const val RESCAN_AFTER_MS = 8_000L
        const val MIN_RESCAN_GAP_MS = 30_000L
    }
}
