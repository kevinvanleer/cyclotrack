package com.kvl.cyclotrack

import javax.inject.Inject

class WeatherRepository @Inject constructor(private val weatherDao: WeatherDao) {
    suspend fun recordWeatherOneCall(
        currentWeatherConditions: CurrentWeatherConditions,
        tripId: Long
    ) {
        weatherDao.save(Weather(currentWeatherConditions, tripId))
    }

    suspend fun recordWeather(currentWeatherConditions: WeatherResponse, tripId: Long) {
        weatherDao.save(Weather(currentWeatherConditions, tripId))
    }

    fun observeLatest() = weatherDao.observeLastestWeather()
    fun observeTripWeather(tripId: Long) = weatherDao.observeTripWeather(tripId)
    suspend fun getTripWeather(tripId: Long) = weatherDao.getTripWeather(tripId)
    suspend fun changeTrip(tripId: Long, newTripId: Long) = weatherDao.changeTrip(tripId, newTripId)
}
