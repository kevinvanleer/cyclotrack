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
data class OnboardSensors(
    val tripId: Long,
    val timestamp: Long = SystemUtils.currentTimeMillis(),
    val gravityX: Float?,
    val gravityY: Float?,
    val gravityZ: Float?,
    val gyroscopeX: Float?,
    val gyroscopeY: Float?,
    val gyroscopeZ: Float?,
    val pressure: Float?,
    val accelerometerX: Float? = null,
    val accelerometerY: Float? = null,
    val accelerometerZ: Float? = null,
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
)
