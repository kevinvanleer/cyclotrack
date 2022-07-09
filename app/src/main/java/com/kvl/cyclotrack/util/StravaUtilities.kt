package com.kvl.cyclotrack.util

import android.content.Context
import android.util.Log
import com.garmin.fit.DateTime
import com.garmin.fit.Mesg
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.*
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
                        else -> {
                            when (response.code) {
                                400, 401 ->
                                    getPreferences(context).edit().apply {
                                        remove(context.getString(R.string.preference_key_strava_refresh_token))
                                        remove(context.getString(R.string.preference_key_strava_access_token))
                                        remove(context.getString(R.string.preference_key_strava_access_expires_at))
                                    }.commit()
                            }
                            throw IOException("Token update failed with response code ${response.code}")
                        }
                    }
                }
            }
    }
}

fun refreshStravaAccessToken(
    appContext: Context,
): String? =
    getPreferences(appContext).getLong(
        appContext.getString(R.string.preference_key_strava_access_expires_at),
        0
    ).let { expiresAt ->
        Log.d("refreshStravaAccessToken", "refreshStravaAccessToken")
        when {
            (SystemUtils.currentTimeMillis() / 1000 + 300) > expiresAt -> {
                getPreferences(appContext).getString(
                    appContext.getString(R.string.preference_key_strava_refresh_token),
                    null
                )?.let { refreshToken ->
                    updateStravaAuthToken(appContext, refreshToken = refreshToken)
                } ?: throw IOException("Not authorized to sync with Strava -- no refresh token")
            }
            else -> getPreferences(appContext).getString(
                appContext.getString(R.string.preference_key_strava_access_token), null
            )
        }
    }

fun sendActivityToStrava(accessToken: String, privateAppFile: File, summary: Trip): Int {
    val logTag = "sendActivityToStrava"
    Log.d("sendActivityToStrava", "sendActivityToStrava")
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
                        Log.d(logTag, response.body?.string() ?: "No body")
                    }
                    return@response response.code
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
    var validAccessToken: String?

    try {
        validAccessToken = refreshStravaAccessToken(appContext)
    } catch (e: Exception) {
        Log.d(logTag, "Strava token refresh failed.")
        FirebaseCrashlytics.getInstance().recordException(e)
        return 401
    }

    Log.d(logTag, "validAccessToken=$validAccessToken")

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

    try {
        validAccessToken ?: getPreferences(appContext).getString(
            appContext.getString(R.string.preference_key_strava_access_token),
            null
        )?.let { accessToken ->
            return sendActivityToStrava(accessToken, privateAppFile, exportData.summary!!).also {
                privateAppFile.delete()
            }
        } ?: Log.d(logTag, "Not authorized to sync with Strava -- no access token")
    } catch (e: Exception) {
        FirebaseCrashlytics.getInstance().recordException(e)
        return 400
    }
    return 401
}

fun deauthorizeStrava(accessToken: String, context: Context) {
    val logTag = "deauthorizeStrava"

    OkHttpClient().let { client ->
        Request.Builder()
            .url("https://www.strava.com/oauth/deauthorize")
            .post(FormBody.Builder().apply { add("access_token", accessToken) }
                .build()).build().let { request ->
                client.newCall(request).execute().let { response ->
                    if (response.isSuccessful) {
                        Log.d(logTag, "STRAVA LOGOUT SUCCESS")
                        getPreferences(context).edit().apply {
                            remove(context.getString(R.string.preference_key_strava_refresh_token))
                            remove(context.getString(R.string.preference_key_strava_access_token))
                            remove(context.getString(R.string.preference_key_strava_access_expires_at))
                        }.commit()
                    } else {
                        Log.d(logTag, "STRAVA LOGOUT ABJECT FAILURE")
                        Log.d(logTag, response.code.toString())
                        Log.d(logTag, response.body?.string() ?: "No body")
                        when (response.code) {
                            401 -> {
                                getPreferences(context).edit().apply {
                                    remove(context.getString(R.string.preference_key_strava_refresh_token))
                                    remove(context.getString(R.string.preference_key_strava_access_token))
                                    remove(context.getString(R.string.preference_key_strava_access_expires_at))
                                }.commit()
                            }
                            else -> {
                                throw IOException("Strava disconnect failed: response code ${response.code}")
                            }
                        }
                    }
                }
            }
    }
}

suspend fun syncTripWithStrava(
    appContext: Context, tripId: Long, tripsRepository: TripsRepository,
    measurementsRepository: MeasurementsRepository,
    timeStateRepository: TimeStateRepository,
    splitRepository: SplitRepository,
    onboardSensorsRepository: OnboardSensorsRepository,
    weatherRepository: WeatherRepository
) {
    val exportData = TripDetailsViewModel.ExportData(
        summary = tripsRepository.get(tripId),
        measurements = measurementsRepository.get(tripId),
        timeStates = timeStateRepository.getTimeStates(tripId),
        splits = splitRepository.getTripSplits(tripId),
        onboardSensors = onboardSensorsRepository.get(tripId),
        weather = weatherRepository.getTripWeather(tripId)
    )
    if (exportData.summary != null &&
        exportData.measurements != null &&
        exportData.timeStates != null &&
        exportData.splits != null &&
        exportData.onboardSensors != null &&
        exportData.weather != null
    ) {
        when (val result = syncTripWithStrava(appContext, tripId, exportData)) {
            in 200..299 -> GoogleFitSyncStatusEnum.SYNCED
            401, 403 -> throw IOException("Strava authentication failure: $result")
            429, in 500..599 -> GoogleFitSyncStatusEnum.NOT_SYNCED
            else -> GoogleFitSyncStatusEnum.FAILED
        }.let { status ->
            tripsRepository.setStravaSyncStatus(
                tripId,
                status
            )
        }
    }
}