package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

@HiltWorker
class StravaSyncTripsWorker @AssistedInject constructor(
    @Assisted val appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    val logTag: String = javaClass.simpleName

    @Inject
    lateinit var tripsRepository: TripsRepository

    override suspend fun doWork(): Result {
        Log.d(logTag, "Syncing with Strava")
        tripsRepository.getStravaUnsynced().forEach { trip ->
            WorkManager.getInstance(appContext)
                .enqueue(
                    OneTimeWorkRequestBuilder<StravaCreateActivityWorker>()
                        .setInputData(workDataOf("tripId" to trip.id)).build().apply {
                            WorkManager.getInstance(appContext)
                                .getWorkInfoById(id).await().let {
                                    when (it.state) {
                                        WorkInfo.State.SUCCEEDED -> tripsRepository.setStravaSyncStatus(
                                            trip.id!!,
                                            GoogleFitSyncStatusEnum.SYNCED
                                        )
                                    }
                                }
                        }
                )
        }
        return Result.success()
    }
}
