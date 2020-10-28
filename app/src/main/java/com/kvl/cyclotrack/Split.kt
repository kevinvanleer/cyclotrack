package com.kvl.cyclotrack

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(foreignKeys = [ForeignKey(entity = Trip::class,
    parentColumns = arrayOf("id"),
    childColumns = arrayOf("tripId"),
    onDelete = ForeignKey.NO_ACTION)],
    indices = [Index(value = ["tripId"])])
data class Split (
    val tripId: Long,
    val timestamp: Long,
    val distance: Double,
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
)