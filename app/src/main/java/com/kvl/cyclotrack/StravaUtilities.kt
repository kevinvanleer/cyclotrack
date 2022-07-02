package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import com.garmin.fit.DateTime
import com.garmin.fit.Mesg
import com.kvl.cyclotrack.data.StravaTokenExchangeResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.*

fun sendActivityToStrava(accessToken: String, privateAppFile: File, summary: Trip): Int {
    val logTag = "sendActivityToStrava"
    return OkHttpClient().let OkClient@{ client ->
        Request.Builder()
            .url("https://www.strava.com/api/v3/uploads")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(
                MultipartBody.Builder().apply {
                    addFormDataPart(
                        "file", privateAppFile.name, privateAppFile.asRequestBody(
                            "application/octet-stream".toMediaTypeOrNull()
                        )
                    )
                    summary.name?.let { name ->
                        addFormDataPart("name", name)
                    }
                    summary.notes?.let { notes ->
                        addFormDataPart("description", notes)
                    }
                    addFormDataPart("trainer", "false")
                    addFormDataPart("commute", "false")
                    addFormDataPart("data_type", "fit")
                    addFormDataPart("external_id", "${summary.id}")
                }.build()
            )
            .build().let { request ->
                client.newCall(request).execute().let response@{ response ->
                    if (response.isSuccessful) {
                        Log.d(logTag, "SUCCESS")
                        //TODO Get strava activity ID
                    } else {
                        Log.d(logTag, "ABJECT FAILURE")
                        Log.d(logTag, response.code.toString())
                    }
                    return@response response.code
                }
            }
    }
}

fun updateStravaAuthToken(
    context: Context,
    authCode: String? = null,
    refreshToken: String? = null
): String? {
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
    return OkHttpClient().let { client ->
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
                client.newCall(request).execute().let response@{ response ->
                    when {
                        response.isSuccessful -> {
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
                            return@response tokenResponse?.access_token
                        }
                        else -> throw IOException("Token update failed with response code ${response.code}")
                    }
                }
            }
    }
}


fun syncTripWithStrava(
    appContext: Context,
    tripId: Long,
    exportData: TripDetailsViewModel.ExportData
): Int {
    val logTag = "syncTripWithStrava"
    val messages: MutableList<Mesg> = makeFitMessages(cyclotrackFitAppId, exportData)

    val privateAppFile = File(
        appContext.filesDir,
        "cyclotrack-trip-$tripId-tmp-${messages.hashCode()}.fit"
    )

    writeFitFile(
        DateTime(Date(exportData.summary!!.timestamp)),
        privateAppFile,
        messages
    )
    getPreferences(appContext).getLong(
        appContext.getString(R.string.preference_key_strava_access_expires_at),
        0
    ).let { expiresAt ->
        if ((SystemUtils.currentTimeMillis() / 1000 + 1800) > expiresAt) {
            getPreferences(appContext).getString(
                appContext.getString(R.string.preference_key_strava_refresh_token),
                null
            )?.let { refreshToken ->
                updateStravaAuthToken(appContext, refreshToken = refreshToken)
            } ?: Log.d(logTag, "Not authorized to sync with Strava -- no refresh token")
        }
    }
    getPreferences(appContext).getString(
        appContext.getString(R.string.preference_key_strava_access_token),
        null
    )?.let { accessToken ->
        return sendActivityToStrava(accessToken, privateAppFile, exportData.summary!!).also {
            privateAppFile.delete()
        }
    } ?: Log.d(logTag, "Not authorized to sync with Strava -- no access token")
    return -1
}

