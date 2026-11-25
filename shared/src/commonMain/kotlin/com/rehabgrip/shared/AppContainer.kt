package com.rehabgrip.shared

import com.rehabgrip.core.ble.ForceSource
import com.rehabgrip.core.ble.SimulatedForceSource
import com.rehabgrip.core.data.DriverFactory
import com.rehabgrip.core.data.SampleData
import com.rehabgrip.core.data.SqlMeasurementRepository
import com.rehabgrip.core.data.SqlSettingsStore
import com.rehabgrip.core.data.createDatabase
import com.rehabgrip.core.domain.export.CsvExporter
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.port.ExportSharer
import com.rehabgrip.core.domain.usecase.SaveMeasurement
import kotlinx.datetime.Clock

/** What each platform must provide (Android activity / iOS entry point). */
interface PlatformServices {
    val driverFactory: DriverFactory
    val exportSharer: ExportSharer
    val isDebug: Boolean
    fun realForceSource(): ForceSource
    suspend fun ensureBluetoothPermission(): Boolean
}

/** Manual dependency injection: one instance per process. */
class AppContainer(val platform: PlatformServices) {
    private val db = createDatabase(platform.driverFactory)
    val measurements = SqlMeasurementRepository(db)
    val settings = SqlSettingsStore(db)
    val saveMeasurement = SaveMeasurement(measurements)
    private val csv = CsvExporter()

    fun now(): Long = Clock.System.now().toEpochMilliseconds()

    fun forceSource(simulated: Boolean): ForceSource =
        if (simulated) SimulatedForceSource() else platform.realForceSource()

    fun export(list: List<Measurement>) =
        platform.exportSharer.share("rehabgrip-${Clock.System.now().toEpochMilliseconds()}.csv", csv.export(list))

    suspend fun loadSampleData() = measurements.replaceAll(SampleData.generate(now()))
}
