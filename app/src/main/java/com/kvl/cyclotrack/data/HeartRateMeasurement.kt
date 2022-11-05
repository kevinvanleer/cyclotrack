package com.kvl.cyclotrack.data

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kvl.cyclotrack.Trip
import com.kvl.cyclotrack.util.SystemUtils

@Entity(
    foreignKeys = [ForeignKey(
        entity = Trip::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("tripId"),
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["tripId"])]
)
@Keep
data class HeartRateMeasurement(
    val tripId: Long,
    val timestamp: Long = SystemUtils.currentTimeMillis(),
    val heartRate: Short,
    val energyExpended: Short?,
    val rrIntervals: String?,
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
)
