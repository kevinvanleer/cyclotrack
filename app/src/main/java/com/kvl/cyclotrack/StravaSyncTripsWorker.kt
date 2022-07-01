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
class StravaSyncTripsWorker @AssistedInject constructor(
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
        Log.d(logTag, "Syncing with Strava")
        tripsRepository.getStravaUnsynced().forEach { trip ->
            trip.id?.let { tripId ->
                Log.d(logTag, "Syncing trip $tripId with Strava")
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
                    when (syncTripWithStrava(appContext, tripId, exportData)) {
                        in 200..299 -> GoogleFitSyncStatusEnum.SYNCED
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
        }
        return Result.success()
    }
}
