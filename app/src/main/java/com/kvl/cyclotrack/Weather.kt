package com.kvl.cyclotrack

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// OpenWeather omits any parameter it did not measure, so every field is optional.
// Only stored fields are declared; Moshi skips the rest.
@Keep
data class CurrentConditions(
    val temp: Double?,
    val feels_like: Double?,
    val pressure: Int?,
    val humidity: Int?,
)

@Keep
data class WindConditions(
    val speed: Double?,
    val deg: Int?,
)

@Keep
data class CloudConditions(
    val all: Int?,
)

@Keep
data class Sys(
    val sunrise: Int?,
    val sunset: Int?,
)

@Keep
data class WeatherResponse(
    val dt: Int?,
    val main: CurrentConditions?,
    val visibility: Int?,
    val wind: WindConditions?,
    val clouds: CloudConditions?,
    val sys: Sys?,
)

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
data class Weather(
    val timestamp: Int,
    val sunrise: Int?,
    val sunset: Int?,
    val temperature: Double,
    val feelsLike: Double?,
    val pressure: Int?,
    val humidity: Int?,
    val dewPoint: Double?,
    val uvIndex: Double?,
    val clouds: Int?,
    val visibility: Int?,
    val windSpeed: Double?,
    val windDirection: Int?,
    val tripId: Long,
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
)

fun WeatherResponse.toWeather(tripId: Long): Weather? {
    val timestamp = dt ?: return null
    val temperature = main?.temp ?: return null
    return Weather(
        tripId = tripId,
        timestamp = timestamp,
        sunrise = sys?.sunrise,
        sunset = sys?.sunset,
        temperature = temperature,
        feelsLike = main.feels_like,
        pressure = main.pressure,
        humidity = main.humidity,
        dewPoint = null,
        uvIndex = null,
        clouds = clouds?.all,
        visibility = visibility,
        windSpeed = wind?.speed,
        windDirection = wind?.deg,
    )
}
