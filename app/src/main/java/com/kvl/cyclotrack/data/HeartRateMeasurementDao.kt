package com.kvl.cyclotrack.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface HeartRateMeasurementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(measurements: HeartRateMeasurement): Long

    @Update
    fun update(measurements: HeartRateMeasurement)

    @Query("SELECT * FROM HeartRateMeasurement WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun load(tripId: Long): Array<HeartRateMeasurement>

    @Query("SELECT * FROM HeartRateMeasurement WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun subscribe(tripId: Long): LiveData<Array<HeartRateMeasurement>>

    @Query("UPDATE HeartRateMeasurement SET tripId = :newTripId WHERE tripId = :tripId")
    suspend fun changeTrip(tripId: Long, newTripId: Long)
}
