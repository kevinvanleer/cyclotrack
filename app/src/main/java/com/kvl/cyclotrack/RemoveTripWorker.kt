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

    override suspend fun doWork(): Result {
        inputData.getLongArray("tripIds")?.takeIf { it.isNotEmpty() }?.forEach { tripId ->
            Log.i(logTag, "Removing data from dB for trip ${tripId}")
            tripsRepository.removeTrip(tripId)
        }
        return Result.success()
    }
}
