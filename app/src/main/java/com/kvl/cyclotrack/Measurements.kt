package com.kvl.cyclotrack

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(foreignKeys = [ForeignKey(entity = Trip::class,
    parentColumns = arrayOf("id"),
    childColumns = arrayOf("tripId"),
    onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["tripId"])])
@Keep
data class Measurements(
    val tripId: Long,
    val accuracy: Float,
    val altitude: Double,
    val bearing: Float,
    val elapsedRealtimeNanos: Long,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val time: Long,
    val bearingAccuracyDegrees: Float = 0f,
    val elapsedRealtimeUncertaintyNanos: Double = 0.0,
    val speedAccuracyMetersPerSecond: Float = 0f,
    val verticalAccuracyMetersPerSecond: Float = 0f,
    val heartRate: Short? = null,
    val cadenceRevolutions: Int? = null,
    val cadenceLastEvent: Int? = null,
    val cadenceRpm: Float? = null,
    val speedRevolutions: Int? = null,
    val speedLastEvent: Int? = null,
    val speedRpm: Float? = null,
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
) {
    constructor(tripId: Long, location: LocationData) : this(tripId,
        location.accuracy,
        location.altitude,
        location.bearing,
        location.elapsedRealtimeNanos,
        location.latitude,
        location.longitude,
        location.speed,
        location.time,
        location.bearingAccuracyDegrees ?: 0f,
        location.elapsedRealtimeUncertaintyNanos ?: 0.0,
        location.speedAccuracyMetersPerSecond ?: 0f,
        location.verticalAccuracyMetersPerSecond ?: 0f)

    constructor(
        tripId: Long,
        location: LocationData,
        heartRate: Short? = null,
        cadenceRevolutions: Int? = null,
        cadenceLastEvent: Int? = null,
        cadenceRpm: Float? = null,
        speedRevolutions: Int? = null,
        speedLastEvent: Int? = null,
        speedRpm: Float? = null,
    ) : this(tripId,
        location.accuracy,
        location.altitude,
        location.bearing,
        location.elapsedRealtimeNanos,
        location.latitude,
        location.longitude,
        location.speed,
        location.time,
        location.bearingAccuracyDegrees ?: 0f,
        location.elapsedRealtimeUncertaintyNanos ?: 0.0,
        location.speedAccuracyMetersPerSecond ?: 0f,
        location.verticalAccuracyMetersPerSecond ?: 0f,
        heartRate,
        cadenceRevolutions,
        cadenceLastEvent,
        cadenceRpm,
        speedRevolutions,
        speedLastEvent,
        speedRpm)

    constructor(
        tripId: Long,
        location: LocationData,
        heartRate: Short? = null,
        cadence: CadenceData? = null,
        speed: SpeedData? = null,
    ) : this(tripId,
        location.accuracy,
        location.altitude,
        location.bearing,
        location.elapsedRealtimeNanos,
        location.latitude,
        location.longitude,
        location.speed,
        location.time,
        location.bearingAccuracyDegrees ?: 0f,
        location.elapsedRealtimeUncertaintyNanos ?: 0.0,
        location.speedAccuracyMetersPerSecond ?: 0f,
        location.verticalAccuracyMetersPerSecond ?: 0f,
        heartRate,
        cadence?.revolutionCount,
        cadence?.lastEvent,
        cadence?.rpm,
        speed?.revolutionCount,
        speed?.lastEvent,
        speed?.rpm)

    fun hasAccuracy(): Boolean = accuracy != 0f
    fun hasSpeedAccuracy(): Boolean = speedAccuracyMetersPerSecond != 0f
}

data class CriticalMeasurements(
    val altitude: Double,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val time: Long,
    val heartRate: Short? = null,
    val cadenceRpm: Float? = null,
    val speedRevolutions: Int? = null,
    val cadenceRevolutions: Int? = null,
    val speedRpm: Float? = null,
)