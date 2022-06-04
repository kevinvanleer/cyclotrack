package com.kvl.cyclotrack

import javax.inject.Inject

class WeatherRepository @Inject constructor(private val weatherDao: WeatherDao) {
    suspend fun recordWeather(currentWeatherConditions: CurrentWeatherConditions, tripId: Long) {
        weatherDao.save(Weather(currentWeatherConditions, tripId))
    }

    fun observeLatest() = weatherDao.observeLastestWeather()
    fun observeTripWeather(tripId: Long) = weatherDao.observeTripWeather(tripId)
    suspend fun getTripWeather(tripId: Long) = weatherDao.getTripWeather(tripId)
}