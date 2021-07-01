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
class RemoveTripWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    val logTag = "RemoveTripWorker"

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var measurementsRepository: MeasurementsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var googleFitApiService: GoogleFitApiService

    override suspend fun doWork(): Result {
        inputData.getLongArray("tripIds")?.takeIf { it.isNotEmpty() }?.forEach { tripId ->
            if (hasFitnessPermissions(applicationContext)) {
                Log.i(logTag, "Removing data from Google Fit for trip ${tripId}")
                try {
                    tripsRepository.get(tripId).let { trip ->
                        timeStateRepository.getTimeStates(trip.id!!).let {
                            googleFitApiService.deleteTrip(trip, it)
                        }
                    }
                } catch (e: NullPointerException) {
                    Log.e(logTag, "Failed to remove Google Fit data for trip ${tripId}")
                    return Result.failure()
                }
            }
            Log.i(logTag, "Removing data from for trip ${tripId}")
            tripsRepository.removeTrip(tripId)
        }
        return Result.success()
    }
}
