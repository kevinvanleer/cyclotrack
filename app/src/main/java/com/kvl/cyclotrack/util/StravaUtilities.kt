package com.kvl.cyclotrack.util

import android.content.Context
import android.util.Log
import com.garmin.fit.DateTime
import com.garmin.fit.Mesg
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.GoogleFitSyncStatusEnum
import com.kvl.cyclotrack.MeasurementsRepository
import com.kvl.cyclotrack.OnboardSensorsRepository
import com.kvl.cyclotrack.R
import com.kvl.cyclotrack.SplitRepository
import com.kvl.cyclotrack.TimeStateRepository
import com.kvl.cyclotrack.Trip
import com.kvl.cyclotrack.TripDetailsViewModel
import com.kvl.cyclotrack.TripsRepository
import com.kvl.cyclotrack.WeatherRepository
import com.kvl.cyclotrack.cyclotrackFitAppId
import com.kvl.cyclotrack.data.CadenceSpeedMeasurementRepository
import com.kvl.cyclotrack.data.HeartRateMeasurementRepository
import com.kvl.cyclotrack.data.StravaTokenExchangeResponse
import com.kvl.cyclotrack.makeFitMessages
import com.kvl.cyclotrack.writeFitFile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Date

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
        when {
            (SystemUtils.currentTimeMillis() / 1000 + 300) > expiresAt -> {
                getPreferences(appContext).getString(
                    appContext.getString(R.string.preference_key_strava_refresh_token),
                    null
                )?.let { refreshToken ->
                    updateStravaAuthToken(appContext, refreshToken = refreshToken)
                }
                    ?: throw AuthenticationFailure("Not authorized to sync with Strava -- no refresh token")
            }

            else -> getPreferences(appContext).getString(
                appContext.getString(R.string.preference_key_strava_access_token), null
            )
        }
    }.also { accessToken ->
        if (accessToken == null) throw AuthenticationFailure("Access token is null")
    }

fun sendActivityToStrava(accessToken: String, privateAppFile: File, summary: Trip): Response {
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
                        Log.d(logTag, response.body?.string() ?: "No body")
                    }
                    return@response response
                }
            }
    }
}

fun syncTripWithStrava(
    appContext: Context,
    tripId: Long,
    exportData: TripDetailsViewModel.ExportData
): Response {
    val validAccessToken = refreshStravaAccessToken(appContext)
        ?: throw AuthenticationFailure("Access token is null")

    val messages: MutableList<Mesg> = makeFitMessages(cyclotrackFitAppId, exportData)

    val privateAppFile = File(
        appContext.filesDir,
        "cyclotrack-trip-$tripId-tmp-${messages.hashCode()}.fit"
    )

    writeFitFile(
        appContext,
        DateTime(Date(exportData.summary!!.timestamp)),
        privateAppFile,
        messages
    )

    return sendActivityToStrava(validAccessToken, privateAppFile, exportData.summary).also {
        privateAppFile.delete()
    }
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

class TooManyRequests(message: String, val limits: List<Int>?, val usage: List<Int>?) :
    IOException(message)

class AuthenticationFailure(message: String) : IOException(message)

suspend fun syncTripWithStrava(
    appContext: Context, tripId: Long, tripsRepository: TripsRepository,
    measurementsRepository: MeasurementsRepository,
    heartRateMeasurementRepository: HeartRateMeasurementRepository,
    cadenceSpeedMeasurementRepository: CadenceSpeedMeasurementRepository,
    timeStateRepository: TimeStateRepository,
    splitRepository: SplitRepository,
    onboardSensorsRepository: OnboardSensorsRepository,
    weatherRepository: WeatherRepository
) {
    val logTag = "syncTripWithStrava"
    val exportData = TripDetailsViewModel.ExportData(
        summary = tripsRepository.get(tripId),
        measurements = measurementsRepository.get(tripId),
        timeStates = timeStateRepository.getTimeStates(tripId),
        splits = splitRepository.getTripSplits(tripId),
        onboardSensors = onboardSensorsRepository.get(tripId),
        weather = weatherRepository.getTripWeather(tripId),
        heartRateMeasurements = heartRateMeasurementRepository.get(tripId),
        speedMeasurements = cadenceSpeedMeasurementRepository.getSpeedMeasurements(tripId),
        cadenceMeasurements = cadenceSpeedMeasurementRepository.getCadenceMeasurements(tripId)
    )
    val now = Instant.now()
    val nextWindow = Instant.parse(
        getPreferences(appContext).getString(
            appContext.getString(R.string.preference_key_strava_next_sync_window),
            now.toString()
        )
    )
    if (nextWindow > now) {
        throw TooManyRequests(
            "Rate limit exceeded, next window begins at: $nextWindow",
            limits = null,
            usage = null
        )
    }
    if (
        exportData.summary != null &&
        exportData.measurements != null &&
        exportData.timeStates != null &&
        exportData.splits != null &&
        exportData.onboardSensors != null &&
        exportData.weather != null
    ) {
        syncTripWithStrava(appContext, tripId, exportData).let { response ->
            val limits =
                response.header("X-RateLimit-Limit")
                    .let { header -> header?.split(',')?.map { it.toInt() } }
            val usage =
                response.header("X-RateLimit-Usage")
                    .let { header -> header?.split(',')?.map { it.toInt() } }
            //NOTE TO SELF: header names seem to be case insensitive
            Log.d(logTag, "${limits.toString()} / ${usage.toString()}")
            if (usage?.size == 2 && limits?.size == 2) {
                when {
                    usage[1] >= limits[1] ->
                        // set backoff to tomorrow
                        Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS)
                            .plusDays(1).toInstant()

                    usage[0] >= limits[0] ->
                        // set backoff to next 15 minute window
                        Instant.now().truncatedTo(ChronoUnit.MINUTES).let { now ->
                            now.plus(
                                15L - now.atZone(ZoneOffset.UTC).minute % 15,
                                ChronoUnit.MINUTES
                            )
                        }

                    else -> Instant.now()
                }.let { nextWindow ->
                    getPreferences(appContext).edit().apply {
                        putString(
                            appContext.getString(R.string.preference_key_strava_next_sync_window),
                            nextWindow.toString()
                        )
                    }.commit()
                }
            }
            when (val result = response.code) {
                401, 403 -> throw AuthenticationFailure("Strava authentication failure: $result")
                429 -> TooManyRequests(
                    "Rate limit exceeded: $result",
                    limits = limits,
                    usage = usage
                ).let { e ->
                    FirebaseCrashlytics.getInstance().recordException(e)
                    throw e
                }

                in 200..299 -> GoogleFitSyncStatusEnum.SYNCED
                in 500..599 -> GoogleFitSyncStatusEnum.NOT_SYNCED
                else -> GoogleFitSyncStatusEnum.FAILED
            }.let { status ->
                tripsRepository.setStravaSyncStatus(
                    tripId,
                    status
                )
            }
        }
    }
}
