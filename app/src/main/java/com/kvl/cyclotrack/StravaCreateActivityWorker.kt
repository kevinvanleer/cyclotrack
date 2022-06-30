package com.kvl.cyclotrack

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
                if (syncTripWithStrava(appContext, tripId, exportData) < 0) {
                    return Result.failure()
                }
            }
        }
        return Result.success()
    }
}
