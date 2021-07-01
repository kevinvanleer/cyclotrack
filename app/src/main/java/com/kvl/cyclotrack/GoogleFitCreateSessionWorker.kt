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
class GoogleFitCreateSessionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    val logTag = "GoogleFitCreateSessWkr"

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var measurementsRepository: MeasurementsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var googleFitApiService: GoogleFitApiService

    override suspend fun doWork(): Result {
        inputData.getLong("tripId", -1).takeIf { it >= 0 }?.let { tripId ->
            Log.i(logTag, "Syncing data with Google Fit for trip ${tripId}")
            try {
                val trip = tripsRepository.get(tripId)
                val timeStates = timeStateRepository.getTimeStates(tripId)
                val measurements = measurementsRepository.getCritical(tripId)

                googleFitApiService.insertDatasets(measurements,
                    getEffectiveCircumference(trip, measurements) ?: getUserCircumference(
                        applicationContext))
                timeStates.takeIf { it.isNotEmpty() }
                    ?.let { googleFitApiService.insertSession(trip, it) }
                    ?: googleFitApiService.insertSession(trip,
                        measurements.first().time,
                        measurements.last().time)

                /*tripsRepository.getAll().forEach { trip ->
                    timeStateRepository.getTimeStates(trip.id!!)?.let {
                        GoogleFitApiService.instance.deleteTrip(trip, it)
                    }
                }*/
            } catch (e: NullPointerException) {
                Log.e(logTag, "Failed to insert trip ${tripId}")
                return Result.failure()
            }
        }
        return Result.success()
    }
}
