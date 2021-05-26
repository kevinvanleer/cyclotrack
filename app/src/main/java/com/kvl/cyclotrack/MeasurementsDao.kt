package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface MeasurementsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(measurements: Measurements): Long

    @Update
    fun update(measurements: Measurements)

    @Query("SELECT * FROM measurements WHERE tripId = :tripId")
    suspend fun load(tripId: Long): Array<Measurements>

    @Query("SELECT * FROM measurements WHERE time = (SELECT max(time) FROM Measurements WHERE tripId = :tripId)")
    suspend fun loadLatest(tripId: Long): Measurements

    @Query("SELECT * FROM measurements WHERE time = (SELECT max(time) FROM Measurements WHERE tripId = :tripId and accuracy < :accuracyThreshold)")
    suspend fun loadLatestAccurate(tripId: Long, accuracyThreshold: Float): Measurements

    @Query("SELECT * FROM measurements WHERE tripId = :tripId")
    fun subscribe(tripId: Long): LiveData<Array<Measurements>>

    @Query("SELECT time,speed,heartRate,speedRevolutions,speedRpm,cadenceRevolutions,cadenceRpm,latitude,longitude,altitude FROM measurements WHERE tripId = :tripId and accuracy < 5")
    suspend fun loadCritical(tripId: Long): Array<CriticalMeasurements>

    @Query("SELECT time,speed,heartRate,speedRevolutions,speedRpm,cadenceRevolutions,cadenceRpm,latitude,longitude,altitude FROM measurements WHERE tripId = :tripId and accuracy < 5")
    fun subscribeCritical(tripId: Long): LiveData<Array<CriticalMeasurements>>

    @Query("SELECT * FROM measurements WHERE time = (SELECT max(time) FROM measurements WHERE tripId = :tripId)")
    fun subscribeLatest(tripId: Long): LiveData<Measurements>

    @Query("UPDATE Measurements SET tripId = :newTripId WHERE tripId = :tripId")
    suspend fun changeTrip(tripId: Long, newTripId: Long)
}