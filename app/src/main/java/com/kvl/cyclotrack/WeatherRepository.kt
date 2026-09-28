package com.kvl.cyclotrack

import javax.inject.Inject

class WeatherRepository @Inject constructor(private val weatherDao: WeatherDao) {
    suspend fun recordWeather(weather: Weather) {
        weatherDao.save(weather)
    }

    fun observeLatest() = weatherDao.observeLastestWeather()
    fun observeTripWeather(tripId: Long) = weatherDao.observeTripWeather(tripId)
    suspend fun getTripWeather(tripId: Long) = weatherDao.getTripWeather(tripId)
    suspend fun changeTrip(tripId: Long, newTripId: Long) = weatherDao.changeTrip(tripId, newTripId)
}
