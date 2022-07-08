package com.kvl.cyclotrack

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
data class Split(
    val tripId: Long,
    val duration: Double,
    val totalDuration: Double,
    val distance: Double,
    val totalDistance: Double,
    val timestamp: Long = SystemUtils.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
)