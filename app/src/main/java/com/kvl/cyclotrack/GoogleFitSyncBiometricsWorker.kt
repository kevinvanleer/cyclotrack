package com.kvl.cyclotrack

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

@HiltWorker
class GoogleFitSyncBiometricsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    val logTag = "GFitSyncBioWorker"

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var googleFitApiService: GoogleFitApiService

    override suspend fun doWork(): Result {
        val height = googleFitApiService.getLatestHeight()
        val weight = googleFitApiService.getLatestWeight()
        val restingHr = googleFitApiService.getLatestRestingHeartRate()
        Log.d(logTag,
            "Updating biometrics preferences: height=${height}; weight=${weight}; restingHr=${restingHr}")
        sharedPreferences.edit {
            height?.let {
                putString(applicationContext.getString(R.string.preference_key_biometrics_user_height),
                    String.format("%.1f",
                        convertSystemToUserHeight(it, applicationContext)))
            }
            weight?.let {
                putString(applicationContext.getString(R.string.preference_key_biometrics_user_weight),
                    String.format("%.1f", convertSystemToUserMass(it, applicationContext)))
            }

            restingHr?.takeIf { FeatureFlags.betaBuild }?.let {
                putString(applicationContext.getString(R.string.preference_key_biometrics_user_restingHeartRate),
                    it.toString())
            }
            commit()
        }
        return Result.success()
    }
}
