package com.kvl.cyclotrack

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.util.*

enum class UserSexEnum(val value: Int) {
    MALE(0),
    FEMALE(1),
}

class UserSexEnumConverter {
    @TypeConverter
    fun fromUserSexEnum(value: UserSexEnum?): Int? {
        return value?.ordinal
    }

    @TypeConverter
    fun toUserSexEnum(value: Int?): UserSexEnum? = value?.let {
        when (it) {
            0 -> UserSexEnum.MALE
            1 -> UserSexEnum.FEMALE
            else -> UserSexEnum.MALE
        }
    }
}

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
@Keep
data class Trip(
    val name: String? = getDefaultTripName(),
    val distance: Double? = 0.0,
    val duration: Double? = null,
    val averageSpeed: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val inProgress: Boolean = true,
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    val notes: String? = null,
    val userWheelCircumference: Float? = null,
    val autoWheelCircumference: Float? = null,
    val userSex: UserSexEnum? = null,
    val userWeight: Float? = null,
    val userHeight: Float? = null,
    val userAge: Float? = null,
    val userVo2max: Float? = null,
    val userRestingHeartRate: Int? = null,
    val userMaxHeartRate: Int? = null,
)