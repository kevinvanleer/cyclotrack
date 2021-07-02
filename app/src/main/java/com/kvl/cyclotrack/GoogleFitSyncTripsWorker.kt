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
            //tripsRepository.getAll().forEach { trip ->
            try {
                /* No longer need to check google fit
                googleFitApiService.getSession(trip)
                    ?.addOnSuccessListener { response ->
                        // Use response data here
                        Log.d(logTag, "found ${response.sessions.size} sessions")
                        response.sessions.forEach { session ->
                            Log.v(logTag, "Session name: ${session.name}")
                            Log.v(logTag, "Session identifier: ${session.identifier}")
                            Log.v(logTag, "Session description: ${session.description}")
                            Log.v(logTag, "Activity type: ${session.activity}")
                        }
                        if (response.sessions.isEmpty()) {
                            Log.d(logTag, "Syncing trip; ID: ${trip.id} name: ${trip.name}")
                            WorkManager.getInstance(applicationContext)
                                .enqueue(OneTimeWorkRequestBuilder<GoogleFitCreateSessionWorker>()
                                    .setInputData(workDataOf("tripId" to trip.id)).build())
                        }
                    }
                    ?.addOnFailureListener { e ->
                        Log.d(logTag,
                            "Failed to get session for trip ${trip.id}",
                            e)
                    }
                 */
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
