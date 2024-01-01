package com.kvl.cyclotrack.data

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kvl.cyclotrack.Trip

@Entity(
    foreignKeys = [ForeignKey(
        entity = Trip::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("tripId"),
        onDelete = ForeignKey.NO_ACTION
    )],
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
