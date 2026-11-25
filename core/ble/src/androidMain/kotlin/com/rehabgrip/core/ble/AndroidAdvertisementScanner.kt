package com.rehabgrip.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Unfiltered scan for the diagnostics screen: every device, every advertisement. */
@SuppressLint("MissingPermission")
class AndroidAdvertisementScanner(context: Context) : AdvertisementScanner {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val _ads = MutableSharedFlow<RawAdvertisement>(extraBufferCapacity = 256)
    override val advertisements: SharedFlow<RawAdvertisement> = _ads.asSharedFlow()
    private val _status = MutableStateFlow(ScaleStatus.IDLE)
    override val status: StateFlow<ScaleStatus> = _status.asStateFlow()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val data = record?.manufacturerSpecificData
            var manufacturer: ByteArray? = null
            if (data != null && data.size() > 0) {
                val key = if (data.indexOfKey(CraneAdvertisementParser.COMPANY_ID) >= 0) CraneAdvertisementParser.COMPANY_ID else data.keyAt(0)
                val payload = data.get(key)
                manufacturer = byteArrayOf((key and 0xFF).toByte(), (key shr 8 and 0xFF).toByte()) + payload
            }
            val name = record?.deviceName ?: runCatching { result.device.name }.getOrNull()
            _ads.tryEmit(RawAdvertisement(result.device.address, name, result.rssi, manufacturer))
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
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        try {
            scanner.startScan(null, settings, callback)
            _status.value = ScaleStatus.SCANNING
        } catch (e: SecurityException) {
            _status.value = ScaleStatus.UNAUTHORIZED
        }
    }

    override fun stop() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        _status.value = ScaleStatus.IDLE
    }
}
