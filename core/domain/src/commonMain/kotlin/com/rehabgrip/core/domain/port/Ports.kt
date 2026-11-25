package com.rehabgrip.core.domain.port

import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** Output port: persistence of measurements (implemented in :core:data). */
interface MeasurementRepository {
    fun observeAll(): Flow<List<Measurement>>
    suspend fun add(measurement: Measurement): Long
    suspend fun delete(id: Long)
    suspend fun replaceAll(measurements: List<Measurement>)
}

/** Output port: the single local user. */
interface ProfileRepository {
    fun observe(): Flow<UserProfile?>
    suspend fun save(profile: UserProfile)
}

/** Output port: hands a CSV file to the platform share sheet. */
fun interface ExportSharer {
    fun share(fileName: String, content: String)
}
