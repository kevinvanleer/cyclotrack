package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.garmin.fit.DateTime
import com.garmin.fit.Mesg
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.*
import javax.inject.Inject

@HiltWorker
class StravaCreateActivityWorker @AssistedInject constructor(
    @Assisted val appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    val logTag: String = javaClass.simpleName

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var measurementsRepository: MeasurementsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var splitRepository: SplitRepository

    @Inject
    lateinit var onboardSensorsRepository: OnboardSensorsRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    override suspend fun doWork(): Result {
        inputData.getLong("tripId", -1).takeIf { it >= 0 }?.let { tripId ->
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
                val messages: MutableList<Mesg> = makeFitMessages(cyclotrackFitAppId, exportData)

                val privateAppFile = java.io.File(
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
                    sendActivityToStrava(accessToken, privateAppFile, exportData.summary!!)
                } ?: Log.d(logTag, "Not authorized to sync with Strava -- no access token")
            }
        }
        return Result.success()
    }

}
