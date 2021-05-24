package com.kvl.cyclotrack

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

@HiltWorker
class StopTripServiceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    @Inject
    lateinit var tripsRepository: TripsRepository

    override suspend fun doWork(): Result {
        tripsRepository.getNewest().let { trip ->
            if (!trip.inProgress) applicationContext.startService(Intent(applicationContext,
                TripInProgressService::class.java).apply {
                this.action = applicationContext.getString(R.string.action_stop_trip_service)
            })
        }
        return Result.success()
    }
}