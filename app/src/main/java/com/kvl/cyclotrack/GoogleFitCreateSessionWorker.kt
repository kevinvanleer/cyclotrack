package com.kvl.cyclotrack

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

@HiltWorker
class GoogleFitCreateSessionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var measurementsRepository: MeasurementsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    override suspend fun doWork(): Result {
        inputData.getLong("tripId", -1).takeIf { it >= 0 }?.let { tripId ->

            val trip = tripsRepository.get(tripId)
            val timeStates = timeStateRepository.getTimeStates(tripId)
            val measurements = measurementsRepository.getCritical(tripId)

            GoogleFitApiService.instance.insertDatasets(measurements,
                getEffectiveCircumference(trip, measurements) ?: getUserCircumference(
                    applicationContext))
            GoogleFitApiService.instance.createSession(trip, timeStates, measurements,
                getEffectiveCircumference(trip, measurements) ?: getUserCircumference(
                    applicationContext))

            /*tripsRepository.getAll().forEach { trip ->
                timeStateRepository.getTimeStates(trip.id!!)?.let {
                    GoogleFitApiService.instance.deleteTrip(trip, it)
                }
            }*/
            return Result.success()
        }
        return Result.failure()
    }
}
