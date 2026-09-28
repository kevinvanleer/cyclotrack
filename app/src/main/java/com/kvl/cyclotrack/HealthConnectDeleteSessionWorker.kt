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
class HealthConnectDeleteSessionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val logTag = "HcDeleteSessionWorker"

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var healthConnectApiService: HealthConnectApiService

    override suspend fun doWork(): Result {
        val tripIds = inputData.getLongArray("tripIds")?.takeIf { it.isNotEmpty() }
            ?: return Result.success()
        if (!healthConnectApiService.canWriteWorkouts()) {
            // Not a failure: this worker runs ahead of RemoveTripWorker in the delete
            // chain, and failing here would strand the ride locally over an unsync
            // that cannot succeed until access is granted again.
            Log.i(logTag, "Cannot remove data, Health Connect write access not granted")
            return Result.success()
        }

        tripIds.forEach { tripId ->
            try {
                // Session, distance and calories go by client id; the streams go by
                // the ride's window, so its bounds have to be read while the trip
                // still exists — this worker runs ahead of RemoveTripWorker.
                val trip = tripsRepository.getOrNull(tripId)
                val timeStates = trip?.let { timeStateRepository.getTimeStates(tripId) }
                healthConnectApiService.deleteTrip(
                    tripId = tripId,
                    start = timeStates?.let { getStartTime(it) } ?: trip?.timestamp ?: 0L,
                    end = timeStates?.let { getEndTime(it) }
                        ?: trip?.let { it.timestamp + ((it.duration ?: 0.0) * 1000).toLong() }
                        ?: 0L,
                )
                trip?.let {
                    tripsRepository.setHealthConnectSyncStatus(
                        tripId,
                        GoogleFitSyncStatusEnum.REMOVED
                    )
                }
            } catch (e: Exception) {
                Log.e(logTag, "Failed to remove trip $tripId from Health Connect", e)
                return Result.failure()
            }
        }
        return Result.success()
    }
}
