package com.kvl.cyclotrack

import androidx.room.*

enum class TimeStateEnum(val value: Int) {
    START(0),
    PAUSE(1),
    RESUME(2),
    STOP(3)
}

class TimeStateEnumConverter {
    @TypeConverter
    fun fromTimeStateEnum (value: TimeStateEnum): Int {
        return value.ordinal
    }
    @TypeConverter
    fun toTimeStateEnum(value: Int): TimeStateEnum {
        return when(value) {
            0 -> TimeStateEnum.START
            1 -> TimeStateEnum.PAUSE
            2 -> TimeStateEnum.RESUME
            3 -> TimeStateEnum.STOP
            else -> TimeStateEnum.STOP
        }
    }
}

@Entity(foreignKeys = [ForeignKey(entity = Trip::class,
    parentColumns = arrayOf("id"),
    childColumns = arrayOf("tripId"),
    onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["tripId"])])
data class TimeState (
    val tripId: Long,
    val state: TimeStateEnum,
    val timestamp: Long = System.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
)
