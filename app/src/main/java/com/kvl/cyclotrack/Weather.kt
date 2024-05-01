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
data class OneCall25WeatherResponse(
    val lat: Double,
    val lon: Double,
    val timezone: String,
    val timezone_offset: Int,
    val current: CurrentWeatherConditions
)

@Keep
data class Coordinate(
    val lat: Double,
    val lon: Double,
)

@Keep
data class CurrentConditions(
    val temp: Double,
    val feels_like: Double,
    val temp_min: Double,
    val temp_max: Double,
    val pressure: Int,
    val humidity: Int,
    val sea_level: Int?,
    val grnd_level: Int?,
)

@Keep
data class WindConditions(
    val speed: Double,
    val deg: Int,
    val gust: Double?,
)

@Keep
data class CloudConditions(
    val all: Int,
)

@Keep
data class Sys(
    val type: Int?,
    val id: Int?,
    val message: String?,
    val country: String?,
    val sunrise: Int,
    val sunset: Int,
)

@Keep
data class CurrentWeatherResponse25(
    val coord: Coordinate,
    val weather: Array<WeatherOverview>,
    val base: String,
    val main: CurrentConditions,
    val visibility: Int,
    val wind: WindConditions,
    val clouds: CloudConditions,
    val dt: Int,
    val sys: Sys,
    val timezone: Int,
    val id: Long,
    val name: String,
    val cod: Int,
)

typealias WeatherResponse = CurrentWeatherResponse25

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

    constructor(current: WeatherResponse, tripId: Long) : this(
        tripId = tripId,
        timestamp = current.dt,
        sunrise = current.sys.sunrise,
        sunset = current.sys.sunset,
        temperature = current.main.temp,
        feelsLike = current.main.feels_like,
        pressure = current.main.pressure,
        humidity = current.main.humidity,
        dewPoint = -1.0,
        uvIndex = -1.0,
        clouds = current.clouds.all,
        visibility = current.visibility,
        windSpeed = current.wind.speed,
        windDirection = current.wind.deg,
    )
}
