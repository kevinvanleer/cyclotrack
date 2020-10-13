package com.kvl.cyclotrack

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(foreignKeys = [ForeignKey(entity = Trip::class,
    parentColumns = arrayOf("id"),
    childColumns = arrayOf("tripId"),
    onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["tripId"])])
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

    fun hasAccuracy(): Boolean = accuracy != 0f
    fun hasSpeedAccuracy(): Boolean = speedAccuracyMetersPerSecond != 0f
}