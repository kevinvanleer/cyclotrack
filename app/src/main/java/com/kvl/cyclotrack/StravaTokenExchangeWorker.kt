package com.kvl.cyclotrack

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kvl.cyclotrack.util.updateStravaAuthToken
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class StravaTokenExchangeWorker @AssistedInject constructor(
    @Assisted val appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val authCode = inputData.getString("authCode")
        val refreshToken = inputData.getString("refreshToken")
        updateStravaAuthToken(
            context = appContext,
            authCode = authCode,
            refreshToken = refreshToken
        )
        return Result.success()
    }
}
