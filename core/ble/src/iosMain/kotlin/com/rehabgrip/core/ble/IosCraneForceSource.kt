package com.rehabgrip.core.ble

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBPeripheral
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSNumber
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * Real WH-C06 on iOS. Scans with AllowDuplicates so CoreBluetooth reports every
 * advertisement (otherwise it coalesces them and the graph freezes). iOS manufacturer
 * data includes the 2 company-ID bytes, stripped before parsing. A 10 s liveness
 * watchdog restarts the scan, as hang-ten does.
 */
class IosCraneForceSource : ForceSource {
    override val name = "Crane Scale WH-C06"
    private val _status = MutableStateFlow(ScaleStatus.IDLE)
    override val status: StateFlow<ScaleStatus> = _status.asStateFlow()
    private val _readings = MutableSharedFlow<ScaleReading>(extraBufferCapacity = 64)
    override val readings: SharedFlow<ScaleReading> = _readings.asSharedFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wantsScan = false
    private var lastPacketAt = 0.0
    private var watchdog: Job? = null
    private var pipeline = ScalePipeline()

    override fun configureLock(lockedId: String?, onSelected: (String, String?) -> Unit) {
        pipeline = ScalePipeline(DeviceLock(lockedId, onSelected = onSelected))
    }

    private val delegate = CentralDelegate(
        onState = { poweredOn, unauthorized ->
            when {
                unauthorized -> _status.value = ScaleStatus.UNAUTHORIZED
                !poweredOn -> _status.value = ScaleStatus.BLUETOOTH_OFF
                wantsScan -> scan()
            }
        },
        onManufacturerData = { id, name, rssi, bytes ->
            // No name or service filter: the lock picks ONE device (peripheral identifier).
            val reading = pipeline.process(id, name, rssi, bytes, (now() * 1000).toLong())
            if (reading != null) {
                lastPacketAt = now()
                _status.value = ScaleStatus.RECEIVING
                _readings.tryEmit(reading)
            }
        },
    )
    private val manager = CBCentralManager(delegate = delegate, queue = null)

    override fun start() {
        wantsScan = true
        if (manager.state == CBManagerStatePoweredOn) scan()
        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                if (wantsScan && now() - lastPacketAt > LIVENESS_TIMEOUT_S && manager.state == CBManagerStatePoweredOn) {
                    if (_status.value == ScaleStatus.RECEIVING) _status.value = ScaleStatus.LOST
                    manager.stopScan()
                    scan()
                }
            }
        }
    }

    private fun scan() {
        manager.scanForPeripheralsWithServices(
            serviceUUIDs = null,
            options = mapOf<Any?, Any?>(CBCentralManagerScanOptionAllowDuplicatesKey to true),
        )
        if (_status.value != ScaleStatus.RECEIVING) _status.value = ScaleStatus.SCANNING
    }

    override fun stop() {
        wantsScan = false
        watchdog?.cancel()
        manager.stopScan()
        _status.value = ScaleStatus.IDLE
    }

    private fun now(): Double = NSDate().timeIntervalSince1970

    private companion object {
        const val HEARTBEAT_MS = 2_000L
        const val LIVENESS_TIMEOUT_S = 10.0
    }
}

private class CentralDelegate(
    private val onState: (poweredOn: Boolean, unauthorized: Boolean) -> Unit,
    private val onManufacturerData: (id: String, name: String?, rssi: Int, bytes: ByteArray) -> Unit,
) : NSObject(), CBCentralManagerDelegateProtocol {

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        onState(central.state == CBManagerStatePoweredOn, central.state == CBManagerStateUnauthorized)
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) {
        val data = advertisementData[CBAdvertisementDataManufacturerDataKey] as? NSData ?: return
        val name = advertisementData[platform.CoreBluetooth.CBAdvertisementDataLocalNameKey] as? String ?: didDiscoverPeripheral.name
        onManufacturerData(didDiscoverPeripheral.identifier.UUIDString, name, RSSI.intValue, data.toByteArray())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
