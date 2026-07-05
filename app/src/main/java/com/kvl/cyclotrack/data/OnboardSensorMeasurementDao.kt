package com.kvl.cyclotrack.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OnboardSensorMeasurementDao {
    @Insert
    suspend fun save(measurements: List<OnboardSensorMeasurement>)

    @Query("SELECT * FROM OnboardSensorMeasurement WHERE tripId = :tripId ORDER BY timestamp, id")
    suspend fun load(tripId: Long): List<OnboardSensorMeasurement>

    @Query("SELECT * FROM OnboardSensorMeasurement WHERE tripId = :tripId ORDER BY timestamp, id")
    fun subscribe(tripId: Long): LiveData<List<OnboardSensorMeasurement>>

    @Query("UPDATE OnboardSensorMeasurement SET tripId = :newTripId WHERE tripId = :tripId")
    suspend fun changeTrip(tripId: Long, newTripId: Long)
}
