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
class RemoveAllHealthConnectDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val logTag = "RemoveAllHcWorker"

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var healthConnectApiService: HealthConnectApiService

    override suspend fun doWork(): Result {
        try {
            healthConnectApiService.deleteAllData()
            tripsRepository.getAll().forEach {
                tripsRepository.setHealthConnectSyncStatus(
                    it.id!!,
                    GoogleFitSyncStatusEnum.REMOVED
                )
            }
        } catch (e: Exception) {
            Log.e(logTag, "Failed to remove data from Health Connect", e)
            return Result.failure()
        }
        return Result.success()
    }
}
