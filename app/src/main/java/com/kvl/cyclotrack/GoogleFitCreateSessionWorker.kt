package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kvl.cyclotrack.data.CadenceSpeedMeasurementRepository
import com.kvl.cyclotrack.data.HeartRateMeasurementRepository
import com.kvl.cyclotrack.util.hasFitnessPermissions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

@HiltWorker
class GoogleFitCreateSessionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    val logTag = "GoogleFitCreateSessWkr"

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var bikeRepository: BikeRepository

    @Inject
    lateinit var measurementsRepository: MeasurementsRepository

    @Inject
    lateinit var cadenceSpeedMeasurementRepository: CadenceSpeedMeasurementRepository

    @Inject
    lateinit var heartRateMeasurementRepository: HeartRateMeasurementRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var googleFitApiService: GoogleFitApiService

    override suspend fun doWork(): Result {
        inputData.getLong("tripId", -1).takeIf { it >= 0 }?.let { tripId ->
            Log.i(logTag, "Syncing data with Google Fit for trip $tripId")
            try {
                if (hasFitnessPermissions(applicationContext)) {
                    val trip = tripsRepository.get(tripId)
                    val timeStates = timeStateRepository.getTimeStates(tripId)
                    val measurements = measurementsRepository.get(tripId)
                    val speedMeasurements =
                        cadenceSpeedMeasurementRepository.getSpeedMeasurements(tripId)

                    googleFitApiService.insertDatasets(
                        measurements = measurements,
                        heartRateMeasurements = heartRateMeasurementRepository.get(tripId),
                        speedMeasurements = speedMeasurements,
                        cadenceMeasurements = cadenceSpeedMeasurementRepository.getCadenceMeasurements(
                            tripId
                        ),
                        wheelCircumference = getEffectiveCircumference(trip, speedMeasurements)
                            ?: userCircumferenceToMeters(bikeRepository.get(trip.bikeId)?.wheelCircumference)
                            ?: 0f
                    )
                    timeStates.takeIf { it.isNotEmpty() }
                        ?.let { googleFitApiService.insertSession(trip, it) }
                        ?: googleFitApiService.insertSession(
                            trip,
                            measurements.first().time,
                            measurements.last().time
                        )

                    tripsRepository.setGoogleFitSyncStatus(tripId, GoogleFitSyncStatusEnum.SYNCED)
                }
            } catch (e: Exception) {
                Log.e(logTag, "Failed to insert trip $tripId", e)
                tripsRepository.setGoogleFitSyncStatus(tripId, GoogleFitSyncStatusEnum.FAILED)
                return Result.failure()
            }
        }
        return Result.success()
    }
}
