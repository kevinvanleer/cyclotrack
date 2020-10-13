package com.kvl.cyclotrack

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

fun getDefaultTripName(): String {
    val c = Calendar.getInstance()

    return when (c.get(Calendar.HOUR_OF_DAY)) {
        in 0..3 -> "Night bike ride"
        in 4..10 -> "Morning bike ride"
        in 11..13 -> "Midday bike ride"
        in 14..17 -> "Afternoon bike ride"
        in 18..20 -> "Evening bike ride"
        in 21..23 -> "Night bike ride"
        else -> "Bike ride"
    }
}

@Entity
data class Trip(
    val name: String? = getDefaultTripName(),
    val distance: Double? = 0.0,
    val duration: Double? = null,
    val averageSpeed: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val inProgress: Boolean = true,
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
)