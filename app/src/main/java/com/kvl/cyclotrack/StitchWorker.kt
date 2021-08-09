package com.kvl.cyclotrack

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

@HiltWorker
class StitchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val logTag = "StitchWorker"

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var measurementsRepository: MeasurementsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var onboardSensorsRepository: OnboardSensorsRepository

    @Inject
    lateinit var splitRepository: SplitRepository

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override suspend fun doWork(): Result {
        Log.d(logTag, "Doing work...")
        val trips = inputData.getLongArray("tripIds") ?: return Result.failure()
        Log.d(logTag, "Stitching trips ${
            trips.map { it.toString() }.reduce { acc, s -> "${acc},${s}" }
        }")
        trips.sort()
        val destinationTrip = tripsRepository.get(trips[0])
        Log.d(logTag, "Trip name: ${destinationTrip.name}")

        trips.filterNot { it == destinationTrip.id }.forEach { tripId ->
            Log.d(logTag, "Processing trip measurements: ${tripId} -> ${destinationTrip.id}")
            measurementsRepository.changeTrip(tripId, destinationTrip.id!!)

            Log.d(logTag, "Processing trip sensors: ${tripId} -> ${destinationTrip.id}")
            onboardSensorsRepository.changeTrip(tripId, destinationTrip.id)

            Log.d(logTag, "Processing trip time states: ${tripId} -> ${destinationTrip.id}")
            timeStateRepository.getTimeStates(tripId).forEach {
                timeStateRepository.update(it.copy(tripId = destinationTrip.id,
                    originalTripId = it.tripId,
                    state = when (it.state) {
                        TimeStateEnum.START -> TimeStateEnum.RESUME
                        TimeStateEnum.STOP -> TimeStateEnum.PAUSE
                        else -> it.state
                    }))
            }
            Log.d(logTag, "Removing trip: ${tripId}")
            tripsRepository.removeTrip(tripId)
        }

        Log.d(logTag, "Resolving time states: ${destinationTrip.id}")
        val lastTimeState = timeStateRepository.getLatest(destinationTrip.id!!)
        if (lastTimeState.state != TimeStateEnum.STOP) {
            timeStateRepository.appendTimeState(lastTimeState.copy(state = TimeStateEnum.STOP))
        }

        Log.d(logTag, "Processing trip splits: ${destinationTrip.id}")
        splitRepository.removeTripSplits(destinationTrip.id)

        val splits = calculateSplits(destinationTrip.id,
            measurementsRepository.getCritical(destinationTrip.id),
            timeStateRepository.getTimeStates(destinationTrip.id),
            PreferenceManager.getDefaultSharedPreferences(applicationContext)).toTypedArray()
        splitRepository.addSplits(splits)

        tripsRepository.updateTripStats(TripStats(id = destinationTrip.id,
            distance = splits.last().totalDistance,
            duration = splits.last().totalDuration,
            averageSpeed = (splits.last().totalDistance / splits.last().totalDuration).toFloat()))

        tripsRepository.updateWheelCircumference(TripWheelCircumference(
            id = destinationTrip.id,
            userWheelCircumference = destinationTrip.userWheelCircumference,
            autoWheelCircumference = destinationTrip.autoWheelCircumference))

        return Result.success()
    }
}