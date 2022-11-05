package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WeatherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(weather: Weather): Long

    @Query("SELECT * FROM Weather WHERE timestamp = (SELECT max(timestamp) FROM Weather)")
    fun observeLastestWeather(): LiveData<Weather>

    @Query("SELECT * FROM Weather WHERE tripId = :tripId")
    suspend fun getTripWeather(tripId: Long): Array<Weather>

    @Query("SELECT * FROM Weather WHERE tripId = :tripId")
    fun observeTripWeather(tripId: Long): LiveData<Array<Weather>>

    @Query("UPDATE Weather SET tripId = :newTripId WHERE tripId = :tripId")
    suspend fun changeTrip(tripId: Long, newTripId: Long)
}
