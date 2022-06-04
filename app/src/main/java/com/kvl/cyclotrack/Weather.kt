package com.kvl.cyclotrack

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Keep
data class WeatherOverview(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

@Keep
data class CurrentWeatherConditions(
    val dt: Int,
    val sunrise: Int,
    val sunset: Int,
    val temp: Double,
    val feels_like: Double,
    val pressure: Int,
    val humidity: Int,
    val dew_point: Double,
    val uvi: Double,
    val clouds: Int,
    val visibility: Int,
    val wind_speed: Double,
    val wind_deg: Int,
    val weather: Array<WeatherOverview>
)

@Keep
data class WeatherResponse(
    val lat: Double,
    val lon: Double,
    val timezone: String,
    val timezone_offset: Int,
    val current: CurrentWeatherConditions
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
    val sunrise: Int,
    val sunset: Int,
    val temperature: Double,
    val feelsLike: Double,
    val pressure: Int,
    val humidity: Int,
    val dewPoint: Double,
    val uvIndex: Double,
    val clouds: Int,
    val visibility: Int,
    val windSpeed: Double,
    val windDirection: Int,
    val tripId: Long,
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
) {
    constructor(current: CurrentWeatherConditions, tripId: Long) : this(
        tripId = tripId,
        timestamp = current.dt,
        sunrise = current.sunrise,
        sunset = current.sunset,
        temperature = current.temp,
        feelsLike = current.feels_like,
        pressure = current.pressure,
        humidity = current.humidity,
        dewPoint = current.dew_point,
        uvIndex = current.uvi,
        clouds = current.clouds,
        visibility = current.visibility,
        windSpeed = current.wind_speed,
        windDirection = current.wind_deg,
    )
}