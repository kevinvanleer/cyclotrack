package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class StravaTokenExchangeWorker @AssistedInject constructor(
    @Assisted val appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val logTag: String = javaClass.simpleName
    override suspend fun doWork(): Result {
        val authCode = inputData.getString("authCode")
        Log.d(logTag, "$authCode")
        return Result.success()
    }
}
