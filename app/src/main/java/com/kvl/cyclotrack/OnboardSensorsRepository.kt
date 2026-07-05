package com.kvl.cyclotrack

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.kvl.cyclotrack.data.OnboardSensorMeasurement
import com.kvl.cyclotrack.data.OnboardSensorMeasurementDao
import com.kvl.cyclotrack.data.OnboardSensorsPivot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// Singleton: the per-trip migration mutexes only serialize ensureMigrated if every
// caller shares this instance. Unscoped injection would hand each worker/viewmodel
// its own mutex map and allow concurrent drains to duplicate a trip's data.
@Singleton
class OnboardSensorsRepository @Inject constructor(
    private val onboardSensorsDao: OnboardSensorsDao,
    private val measurementDao: OnboardSensorMeasurementDao,
    private val db: TripsDatabase,
) {
    private val migrateMutexes = ConcurrentHashMap<Long, Mutex>()

    suspend fun insertSensorMeasurements(measurements: List<OnboardSensorMeasurement>) =
        measurementDao.save(measurements)

    suspend fun get(tripId: Long): Array<OnboardSensorMeasurement> {
        ensureMigrated(tripId)
        return measurementDao.load(tripId).toTypedArray()
    }

    suspend fun changeTrip(tripId: Long, newTripId: Long) {
        onboardSensorsDao.changeTrip(tripId, newTripId)
        measurementDao.changeTrip(tripId, newTripId)
    }

    suspend fun legacyTripIds() = onboardSensorsDao.tripIds()

    suspend fun hasLegacyRows() = onboardSensorsDao.hasRows()

    data class MigrationProgress(val migrated: Int, val total: Int)

    suspend fun migrationProgress() = MigrationProgress(
        migrated = onboardSensorsDao.migratedTripCount(),
        total = onboardSensorsDao.sensorTripCount(),
    )

    // ponytail: single-process mutexes; fine while only the app process touches the db
    suspend fun ensureMigrated(tripId: Long) =
        migrateMutexes.computeIfAbsent(tripId) { Mutex() }.withLock {
            val pivot = OnboardSensorsPivot()
            while (true) {
                val chunk = onboardSensorsDao.loadChunk(tripId, pivot.lastId, MIGRATE_CHUNK_SIZE)
                if (chunk.isEmpty()) break
                val measurements = pivot.pivot(chunk)
                try {
                    db.withTransaction {
                        measurementDao.save(measurements)
                        onboardSensorsDao.deleteChunk(tripId, pivot.lastId)
                    }
                } catch (e: SQLiteConstraintException) {
                    // trip deleted between loadChunk and save: the cascade already wiped
                    // both tables' rows, so there is nothing left to migrate
                    if (!onboardSensorsDao.tripExists(tripId)) return@withLock
                    throw e
                }
            }
        }

    companion object {
        const val MIGRATE_CHUNK_SIZE = 10_000
    }
}
