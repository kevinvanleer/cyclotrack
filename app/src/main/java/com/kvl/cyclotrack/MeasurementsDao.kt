package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MeasurementsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(measurements: Measurements): Long

    @Query("SELECT * FROM measurements WHERE tripId = :tripId")
    fun load(tripId: Long): LiveData<Array<Measurements>>

    @Query("SELECT time,speed,heartRate,speedRevolutions,speedRpm,cadenceRevolutions,cadenceRpm,latitude,longitude,altitude FROM measurements WHERE tripId = :tripId and accuracy < 5")
    fun loadDetails(tripId: Long): LiveData<Array<CriticalMeasurements>>

    @Query("SELECT * FROM measurements WHERE id = (SELECT max(id) FROM measurements WHERE tripId = :tripId)")
    fun getLastMeasurement(tripId: Long): LiveData<Measurements>
}