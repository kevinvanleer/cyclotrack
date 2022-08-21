package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kvl.cyclotrack.util.syncTripWithStrava
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant
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
        var startTime = Instant.now()
        var periodSyncCount = 0
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

            periodSyncCount++
            if (Instant.now() < startTime + Duration.ofMinutes(15)) {
                if (periodSyncCount >= 100) {
                    return Result.retry()
                }
            } else {
                periodSyncCount = 0
                startTime = Instant.now()
            }
        }
        return Result.success()
    }
}
