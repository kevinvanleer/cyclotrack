package com.kvl.cyclotrack.util

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import com.kvl.cyclotrack.FeatureFlags

// Cadence has no permission of its own — it resolves to WRITE_EXERCISE, which the
// set dedupes.
val healthConnectWritePermissions: Set<String> = setOf(
    HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    HealthPermission.getWritePermission(DistanceRecord::class),
    HealthPermission.getWritePermission(HeartRateRecord::class),
    HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
    HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
    HealthPermission.getWritePermission(PowerRecord::class),
    HealthPermission.getWritePermission(SpeedRecord::class),
    HealthPermission.getWritePermission(CyclingPedalingCadenceRecord::class),
    HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
)

val healthConnectReadPermissions: Set<String> = setOf(
    HealthPermission.getReadPermission(HeightRecord::class),
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getReadPermission(RestingHeartRateRecord::class),
)

val healthConnectPermissions: Set<String> =
    healthConnectWritePermissions + healthConnectReadPermissions

fun healthConnectSdkStatus(context: Context) = HealthConnectClient.getSdkStatus(context)

// The health permissions are stripped from the prod manifest, so gate every
// caller on the one flag rather than letting prod ask for what it cannot hold.
fun isHealthConnectAvailable(context: Context) =
    FeatureFlags.betaBuild && healthConnectSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

suspend fun grantedHealthConnectPermissions(context: Context): Set<String> =
    when (isHealthConnectAvailable(context)) {
        true -> HealthConnectClient.getOrCreate(context)
            .permissionController.getGrantedPermissions()

        false -> emptySet()
    }

suspend fun hasHealthConnectWriteAccess(context: Context) =
    grantedHealthConnectPermissions(context).containsAll(healthConnectWritePermissions)
