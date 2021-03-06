package com.kvl.cyclotrack

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

@HiltWorker
class GoogleFitSyncTripsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    val logTag = "GoogleFitSyncTripWorker"

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var measurementsRepository: MeasurementsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var googleFitApiService: GoogleFitApiService

    override suspend fun doWork(): Result {
        Log.d(logTag, "Syncing sessions")
        tripsRepository.getGoogleFitUnsynced().forEach { trip ->
            try {

                Log.d(logTag, "Syncing trip; ID: ${trip.id} name: ${trip.name}")
                WorkManager.getInstance(applicationContext)
                    .enqueue(OneTimeWorkRequestBuilder<GoogleFitCreateSessionWorker>()
                        .setInputData(workDataOf("tripId" to trip.id)).build())
            } catch (e: NullPointerException) {
                Log.e(logTag, "Trip contains invalid data, don't sync", e)
                tripsRepository.setGoogleFitSyncStatus(trip.id!!, GoogleFitSyncStatusEnum.FAILED)

            }
        }
        return Result.success()
    }
}
