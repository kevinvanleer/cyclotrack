package com.kvl.cyclotrack

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
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

    override suspend fun doWork(): Result {
        val newestSyncedTrip = sharedPreferences.getLong("com.kvl.cyclotrack.newestSyncedTrip", -1)
        Log.d(logTag, "newestSyncedTripId=${newestSyncedTrip}")
        tripsRepository.getAll().filter { it.id!! > newestSyncedTrip }.forEach { trip ->
            //tripsRepository.getAll().forEach { trip ->
            try {
                GoogleFitApiService.instance.getSession(trip)
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
                        if (trip.id!! > newestSyncedTrip) {
                            sharedPreferences.edit {
                                putLong("com.kvl.cyclotrack.newestSyncedTrip", trip.id!!)
                                apply()
                            }
                        }
                    }
                    ?.addOnFailureListener { e -> Log.d(logTag, "getSessions::OnFailure()", e) }
            } catch (e: NullPointerException) {
                Log.e(logTag, "Trip contains invalid data, don't sync", e)

            }
        }
        return Result.success()
    }
}
