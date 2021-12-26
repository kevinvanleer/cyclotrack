package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var googleFitApiService: GoogleFitApiService

    override suspend fun doWork(): Result {
        inputData.getLong("tripId", -1).takeIf { it >= 0 }?.let { tripId ->
            Log.i(logTag, "Syncing data with Google Fit for trip ${tripId}")
            try {
                if (hasFitnessPermissions(applicationContext)) {
                    val trip = tripsRepository.get(tripId)
                    val timeStates = timeStateRepository.getTimeStates(tripId)
                    val measurements = measurementsRepository.getCritical(tripId)

                    googleFitApiService.insertDatasets(
                        measurements,
                        getEffectiveCircumference(trip, measurements)
                            ?: userCircumferenceToMeters(bikeRepository.get(trip.bikeId).wheelCircumference)
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
                Log.e(logTag, "Failed to insert trip ${tripId}", e)
                tripsRepository.setGoogleFitSyncStatus(tripId, GoogleFitSyncStatusEnum.FAILED)
                return Result.failure()
            }
        }
        return Result.success()
    }
}
