package com.kvl.cyclotrack

import androidx.annotation.Keep
import androidx.room.*

enum class TimeStateEnum(val value: Int) {
    START(0),
    RESUME(1),
    PAUSE(2),
    STOP(3)
}

class TimeStateEnumConverter {
    @TypeConverter
    fun fromTimeStateEnum(value: TimeStateEnum): Int {
        return value.ordinal
    }

    @TypeConverter
    fun toTimeStateEnum(value: Int): TimeStateEnum {
        return when (value) {
            0 -> TimeStateEnum.START
            1 -> TimeStateEnum.RESUME
            2 -> TimeStateEnum.PAUSE
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
@Keep
data class TimeState(
    val tripId: Long,
    val state: TimeStateEnum,
    val timestamp: Long = SystemUtils.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    val originalTripId: Long? = tripId,
)
