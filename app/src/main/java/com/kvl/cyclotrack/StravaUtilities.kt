package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import com.kvl.cyclotrack.data.StravaTokenExchangeResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

fun sendActivityToStrava(accessToken: String, privateAppFile: File, summary: Trip): Int {
    val logTag = "sendActivityToStrava"
    Log.d(logTag, privateAppFile.readText())
    OkHttpClient().let { client ->
        Request.Builder()
            .url("https://www.strava.com/api/v3/uploads")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(
                FormBody.Builder().apply {
                    addEncoded("file", privateAppFile.readText())
                    summary.notes?.let { name ->
                        add("name", name)
                    }
                    summary.notes?.let { notes ->
                        add("description", notes)
                    }
                    add("trainer", "false")
                    add("commute", "false")
                    add("data_type", "fit")
                    add("external_id", "${summary.id}")
                }.build()
            )
            .build().let { request ->
                client.newCall(request).execute().let { response ->
                    if (response.isSuccessful) {
                        Log.d(logTag, "SUCCESS")
                        Log.d(logTag, response.body.toString())
                        return 0
                    } else {
                        Log.d(logTag, "ABJECT FAILURE")
                        Log.d(logTag, response.code.toString())
                        Log.d(logTag, response.message)
                        return -1
                    }
                }
            }
    }
}

fun updateStravaAuthToken(
    context: Context,
    authCode: String? = null,
    refreshToken: String? = null
) {
    val logTag = "updateStravaAuthToken"
    if (authCode == null && refreshToken == null) {
        throw RuntimeException("Strava token exchange must have an authorization code or refresh token")
    }
    val grantType = when (refreshToken) {
        null -> "authorization_code"
        else -> "refresh_token"
    }
    val grantKey = when (refreshToken) {
        null -> "code"
        else -> "refresh_token"
    }
    val token = authCode ?: refreshToken!!
    val jsonAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(StravaTokenExchangeResponse::class.java)
    OkHttpClient().let { client ->
        Request.Builder()
            .url("https://www.strava.com/oauth/token")
            .post(
                FormBody.Builder()
                    .add("client_id", context.getString(R.string.strava_client_id))
                    .add(
                        "client_secret",
                        context.getString(R.string.strava_client_secret)
                    )
                    .add(grantKey, token)
                    .add("grant_type", grantType)
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
                                Log.d(logTag, "$tokenResponse")
                                Log.d(logTag, "${tokenResponse?.expires_in}")
                                Log.d(logTag, "${tokenResponse?.refresh_token}")
                                Log.d(logTag, "${tokenResponse?.access_token}")
                                Log.d(logTag, "${tokenResponse?.athlete}")
                                getPreferences(context).edit().apply {
                                    putLong(
                                        context.getString(R.string.preference_key_strava_access_expires_at),
                                        tokenResponse!!.expires_at
                                    )
                                    putString(
                                        context.getString(R.string.preference_key_strava_refresh_token),
                                        tokenResponse.refresh_token
                                    )
                                    putString(
                                        context.getString(R.string.preference_key_strava_access_token),
                                        tokenResponse.access_token
                                    )
                                }.commit()
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
}

