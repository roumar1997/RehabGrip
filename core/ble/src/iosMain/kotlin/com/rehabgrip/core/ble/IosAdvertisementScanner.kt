package com.rehabgrip.core.ble

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBPeripheral
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.darwin.NSObject
import platform.posix.memcpy

/** Unfiltered CoreBluetooth scan (no services, duplicates allowed) for the diagnostics screen. */
class IosAdvertisementScanner : AdvertisementScanner {
    private val _ads = MutableSharedFlow<RawAdvertisement>(extraBufferCapacity = 256)
    override val advertisements: SharedFlow<RawAdvertisement> = _ads.asSharedFlow()
    private val _status = MutableStateFlow(ScaleStatus.IDLE)
    override val status: StateFlow<ScaleStatus> = _status.asStateFlow()
    private var wantsScan = false

    private val delegate = DiagnosticsDelegate(
        onState = { poweredOn, unauthorized ->
            when {
                unauthorized -> _status.value = ScaleStatus.UNAUTHORIZED
                !poweredOn -> _status.value = ScaleStatus.BLUETOOTH_OFF
                wantsScan -> scan()
            }
        },
        onAd = { _ads.tryEmit(it) },
    )
    private val manager = CBCentralManager(delegate = delegate, queue = null)

    override fun start() {
        wantsScan = true
        if (manager.state == CBManagerStatePoweredOn) scan()
    }

    private fun scan() {
        manager.scanForPeripheralsWithServices(
            serviceUUIDs = null,
            options = mapOf<Any?, Any?>(CBCentralManagerScanOptionAllowDuplicatesKey to true),
        )
        _status.value = ScaleStatus.SCANNING
    }

    override fun stop() {
        wantsScan = false
        manager.stopScan()
        _status.value = ScaleStatus.IDLE
    }
}

private class DiagnosticsDelegate(
    private val onState: (poweredOn: Boolean, unauthorized: Boolean) -> Unit,
    private val onAd: (RawAdvertisement) -> Unit,
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
        val data = (advertisementData[CBAdvertisementDataManufacturerDataKey] as? NSData)?.toBytes()
        val name = advertisementData[CBAdvertisementDataLocalNameKey] as? String ?: didDiscoverPeripheral.name
        onAd(RawAdvertisement(didDiscoverPeripheral.identifier.UUIDString, name, RSSI.intValue, data))
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toBytes(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
