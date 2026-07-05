package com.kvl.cyclotrack

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.util.TelemetryEvent
import com.kvl.cyclotrack.util.emitTelemetry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltWorker
class OnboardSensorsMigrationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val logTag = "OnboardSensorsMigration"

    @Inject
    lateinit var onboardSensorsRepository: OnboardSensorsRepository

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override suspend fun doWork(): Result = try {
        val tripIds = onboardSensorsRepository.legacyTripIds()
        if (!sharedPreferences.getBoolean(PREF_STARTED_EVENT, false)) {
            emitTelemetry(TelemetryEvent.OnboardSensorsMigrationStarted(tripIds.size))
            sharedPreferences.edit {
                putBoolean(PREF_STARTED_EVENT, true)
                putInt(PREF_RIDES_TO_MIGRATE, tripIds.size)
            }
        }
        var failures = 0
        tripIds.forEachIndexed { index, tripId ->
            // value unused by the UI; each update is a change signal to re-query counts
            setProgress(workDataOf(PROGRESS_REMAINING to tripIds.size - index))
            Log.d(logTag, "Migrating onboard sensors for trip $tripId")
            try {
                onboardSensorsRepository.ensureMigrated(tripId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                Log.e(logTag, "Failed to migrate trip $tripId", e)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
        if (failures > 0) {
            if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        } else {
            if (!sharedPreferences.getBoolean(PREF_FINISHED_EVENT, false) &&
                !onboardSensorsRepository.hasLegacyRows()
            ) {
                emitTelemetry(
                    TelemetryEvent.OnboardSensorsMigrationFinished(
                        sharedPreferences.getInt(PREF_RIDES_TO_MIGRATE, tripIds.size)
                    )
                )
                sharedPreferences.edit { putBoolean(PREF_FINISHED_EVENT, true) }
            }
            Result.success()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(logTag, "Onboard sensor migration failed", e)
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "OnboardSensorsMigrationWorker"
        const val MAX_ATTEMPTS = 5
        const val PROGRESS_REMAINING = "remaining"
        const val PREF_STARTED_EVENT = "onboard_sensors_migration_started_event"
        const val PREF_FINISHED_EVENT = "onboard_sensors_migration_finished_event"
        const val PREF_RIDES_TO_MIGRATE = "onboard_sensors_migration_ride_count"
    }
}
