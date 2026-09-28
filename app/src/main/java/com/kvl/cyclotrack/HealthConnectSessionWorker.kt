package com.kvl.cyclotrack

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

// One ride, on request. The backlog drain writes rides itself; this exists for the
// Sync menu, where the ride is the unit the user acted on.
@HiltWorker
class HealthConnectSessionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @Inject
    lateinit var healthConnectSyncer: HealthConnectSyncer

    override suspend fun doWork(): Result {
        val tripId = inputData.getLong("tripId", -1).takeIf { it >= 0 } ?: return Result.success()
        if (!healthConnectSyncer.canWrite()) return Result.success()

        healthConnectSyncer.sync(tripId)
        return Result.success()
    }
}

// A ride's sync is unique work keyed on the ride, so a second request while one is in
// flight is dropped rather than run alongside it.
//
// KEEP rather than REPLACE: a replacement only cancels cooperatively, so it would
// start writing while the outgoing worker was still mid-write. Finished work does not
// block a requeue, so a manual re-sync still runs.
fun enqueueHealthConnectSession(context: Context, tripId: Long) {
    WorkManager.getInstance(context).enqueueUniqueWork(
        healthConnectSessionWorkName(tripId),
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<HealthConnectSessionWorker>()
            .setInputData(workDataOf("tripId" to tripId))
            .build()
    )
}

fun healthConnectSessionWorkName(tripId: Long) = "health-connect-trip-$tripId"
