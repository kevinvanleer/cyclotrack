package com.kvl.cyclotrack

import android.util.Log
import com.kvl.cyclotrack.data.CadenceSpeedMeasurementRepository
import com.kvl.cyclotrack.data.CyclingPowerMeasurementRepository
import com.kvl.cyclotrack.data.HeartRateMeasurementRepository
import javax.inject.Inject
import javax.inject.Singleton

// Syncing one ride, shared by the backlog drain and the per-ride Sync menu so the
// two cannot drift apart.
@Singleton
class HealthConnectSyncer @Inject constructor(
    private val healthConnectApiService: HealthConnectApiService,
    private val tripsRepository: TripsRepository,
    private val bikeRepository: BikeRepository,
    private val timeStateRepository: TimeStateRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val cadenceSpeedMeasurementRepository: CadenceSpeedMeasurementRepository,
    private val powerMeasurementRepository: CyclingPowerMeasurementRepository,
    private val heartRateMeasurementRepository: HeartRateMeasurementRepository,
) {
    private val logTag = "HealthConnectSyncer"

    suspend fun canWrite() = healthConnectApiService.canWriteWorkouts()

    suspend fun sync(tripId: Long): Boolean = try {
        val trip = tripsRepository.get(tripId)
        val speedMeasurements = cadenceSpeedMeasurementRepository.getSpeedMeasurements(tripId)

        healthConnectApiService.upsertSession(
            trip = trip,
            timeStates = timeStateRepository.getTimeStates(tripId),
            measurements = measurementsRepository.get(tripId),
            heartRateMeasurements = heartRateMeasurementRepository.get(tripId),
            speedMeasurements = speedMeasurements,
            cadenceMeasurements = cadenceSpeedMeasurementRepository.getCadenceMeasurements(tripId),
            powerMeasurements = powerMeasurementRepository.getPowerMeasurements(tripId),
            wheelCircumference = getEffectiveCircumference(trip, speedMeasurements)
                ?: userCircumferenceToMeters(bikeRepository.get(trip.bikeId)?.wheelCircumference)
                ?: 0f,
        ).also { wrote ->
            // Only a ride that actually reached Health Connect is marked synced.
            // Anything skipped keeps its status so the next drain sees it again.
            if (wrote) {
                tripsRepository.setHealthConnectSyncStatus(tripId, GoogleFitSyncStatusEnum.SYNCED)
            }
        }
    } catch (e: Exception) {
        Log.e(logTag, "Failed to write trip $tripId to Health Connect", e)
        tripsRepository.setHealthConnectSyncStatus(tripId, GoogleFitSyncStatusEnum.FAILED)
        false
    }
}
