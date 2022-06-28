package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kvl.cyclotrack.data.StravaTokenExchangeResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltWorker
class StravaTokenExchangeWorker @AssistedInject constructor(
    @Assisted val appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val logTag: String = javaClass.simpleName
    override suspend fun doWork(): Result {
        val authCode = inputData.getString("authCode")!!
        Log.d(logTag, authCode)
        val jsonAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter(StravaTokenExchangeResponse::class.java)
        OkHttpClient().let { client ->
            Request.Builder()
                .url("https://www.strava.com/oauth/token")
                .post(
                    FormBody.Builder()
                        .add("client_id", R.string.strava_client_id)
                        .add("client_secret", R.string.strava_client_secret)
                        .add("code", authCode)
                        .add("grant_type", "authorization_code")
                        .build()
                )
                .build()
                .let { request ->
                    try {
                        client.newCall(request).execute().let { response ->
                            if (response.isSuccessful) {
                                try {
                                    val tokenResponse = response.body?.source()?.let {
                                        jsonAdapter.nullSafe().fromJson(it)
                                    }
                                    Log.d(logTag, "${tokenResponse?.expires_at}")
                                    Log.d(logTag, "${tokenResponse?.expires_in}")
                                    Log.d(logTag, "${tokenResponse?.refresh_token}")
                                    Log.d(logTag, "${tokenResponse?.athlete}")
                                } catch (e: Exception) {
                                    Log.e(logTag, "ERROR", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(logTag, "ERROR", e)
                    }
                }
        }
        return Result.success()
    }
}
