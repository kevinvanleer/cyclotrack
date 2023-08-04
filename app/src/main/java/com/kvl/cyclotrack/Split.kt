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
    val duration: Double = 0.0,
    val totalDuration: Double = 0.0,
    val distance: Double = 0.0,
    val totalDistance: Double = 0.0,
    val timestamp: Long = SystemUtils.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is Split) return false
        return this.duration == other.duration &&
                this.distance == other.distance &&
                this.totalDuration == other.totalDuration &&
                this.totalDistance == other.totalDistance &&
                this.timestamp == other.timestamp &&
                this.tripId == other.tripId
    }
}