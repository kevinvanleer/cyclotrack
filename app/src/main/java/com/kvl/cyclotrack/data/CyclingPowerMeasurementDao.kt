package com.kvl.cyclotrack.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CyclingPowerMeasurementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(measurements: CyclingPowerMeasurement): Long

    @Update
    fun update(measurements: CyclingPowerMeasurement)

    @Query("SELECT * FROM CyclingPowerMeasurement WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun load(tripId: Long): Array<CyclingPowerMeasurement>

    @Query("SELECT * FROM CyclingPowerMeasurement WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun subscribe(tripId: Long): LiveData<Array<CyclingPowerMeasurement>>

    @Query("SELECT id, tripId, timestamp, cumulativeCrankRevolutions as revolutions, lastCrankEventTime as lastEvent, rpm, 2 as sensorType FROM CyclingPowerMeasurement WHERE tripId = :tripId and cumulativeCrankRevolutions IS NOT NULL and lastCrankEventTime IS NOT NULL ORDER BY timestamp ASC")
    fun subscribeCadence(tripId: Long): LiveData<Array<CadenceSpeedMeasurement>>
}
