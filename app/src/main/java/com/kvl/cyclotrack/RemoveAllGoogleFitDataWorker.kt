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
class RemoveAllGoogleFitDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    val logTag = "RemoveAllGFitWorker"

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var googleFitApiService: GoogleFitApiService

    override suspend fun doWork(): Result {
        try {
            googleFitApiService.deleteAllData()
            tripsRepository.getAll().forEach {
                tripsRepository.setGoogleFitSyncStatus(it.id!!,
                    GoogleFitSyncStatusEnum.REMOVED)
            }
        } catch (e: Exception) {
            Log.e(logTag, "Failed to remove data from Google Fit", e)
            return Result.failure()
        }
        return Result.success()
    }
}
