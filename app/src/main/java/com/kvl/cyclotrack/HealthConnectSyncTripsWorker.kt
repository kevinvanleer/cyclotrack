package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

// Drains the whole backlog in one run, a ride at a time.
//
// Sequential rather than a worker per ride: each ride's measurements are loaded in
// full, so fanning out lets several rides' data pile up in the heap at once — the
// OOM the Strava sync documents. One at a time bounds that to a single ride no
// matter how far behind the backlog is.
//
// Restartable rather than resumable: a ride's status flips as it lands, so a worker
// killed partway is simply re-run and picks up where it stopped.
@HiltWorker
class HealthConnectSyncTripsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val logTag = "HcSyncTripsWorker"

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var healthConnectSyncer: HealthConnectSyncer

    override suspend fun doWork(): Result {
        if (!healthConnectSyncer.canWrite()) return Result.success()

        val pending = tripsRepository.getHealthConnectPending()
        if (pending.isEmpty()) return Result.success()
        Log.i(logTag, "${pending.size} rides pending for Health Connect")

        pending.forEach { trip ->
            if (isStopped) {
                Log.i(logTag, "Stopped with ${pending.size} rides outstanding")
                return Result.retry()
            }
            healthConnectSyncer.sync(trip.id!!)
        }
        return Result.success()
    }
}
