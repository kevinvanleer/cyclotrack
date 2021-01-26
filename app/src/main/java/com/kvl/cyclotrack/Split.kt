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
data class Split(
    val tripId: Long,
    val duration: Double,
    val totalDuration: Double,
    val distance: Double,
    val totalDistance: Double,
    val timestamp: Long = System.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
)