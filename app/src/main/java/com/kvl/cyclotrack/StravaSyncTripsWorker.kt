package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kvl.cyclotrack.util.syncTripWithStrava
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
                syncTripWithStrava(
                    appContext, tripId, tripsRepository,
                    measurementsRepository,
                    timeStateRepository,
                    splitRepository,
                    onboardSensorsRepository,
                    weatherRepository
                )
            }
        }
        return Result.success()
    }
}
