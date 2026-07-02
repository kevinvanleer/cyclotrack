package com.kvl.cyclotrack.data

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [Index(value = ["tripId"])]
)
@Keep
data class Export(
    val timestamp: Long = System.currentTimeMillis(),
    val tripId: Long,
    val uri: String,
    val filename: String,
    val fileType: String,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0
)
