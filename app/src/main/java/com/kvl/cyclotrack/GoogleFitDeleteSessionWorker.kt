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
class GoogleFitDeleteSessionWorker @AssistedInject constructor(
    @Assisted val appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    val logTag = "GFitDeleteSessionWorker"

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var googleFitApiService: GoogleFitApiService

    override suspend fun doWork(): Result {
        if (!hasFitnessPermissions(appContext)) {
            Log.i(logTag, "Cannot remove data, user not logged in")
            return Result.failure()
        }
        inputData.getLongArray("tripIds")?.takeIf { it.isNotEmpty() }?.forEach { tripId ->
            try {
                tripsRepository.get(tripId).let { trip ->
                    if (trip.googleFitSyncStatus == GoogleFitSyncStatusEnum.SYNCED) {
                        Log.i(logTag, "Removing data from Google Fit for trip ${tripId}")
                        timeStateRepository.getTimeStates(trip.id!!).let {
                            googleFitApiService.deleteTrip(trip, it)
                        }
                        tripsRepository.setGoogleFitSyncStatus(trip.id!!,
                            GoogleFitSyncStatusEnum.REMOVED)
                    } else {
                        Log.i(logTag, "Trip ${tripId} already synced")
                    }
                }
            } catch (e: NullPointerException) {
                Log.e(logTag, "Failed to remove Google Fit data for trip ${tripId}")
                return Result.failure()
            }
        }
        return Result.success()
    }
}
