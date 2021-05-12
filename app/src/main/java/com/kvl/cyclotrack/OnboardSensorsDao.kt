package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface OnboardSensorsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(measurements: OnboardSensors): Long

    @Update
    fun update(measurements: OnboardSensors)

    @Query("UPDATE OnboardSensors SET tripId = :newTripId WHERE tripId = :tripId")
    suspend fun changeTrip(tripId: Long, newTripId: Long)

    @Query("SELECT * FROM OnboardSensors WHERE tripId = :tripId")
    suspend fun load(tripId: Long): Array<OnboardSensors>

    @Query("SELECT * FROM OnboardSensors WHERE tripId = :tripId")
    fun subscribe(tripId: Long): LiveData<Array<OnboardSensors>>

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Query("select obs1.* from OnboardSensors as obs1 left join OnboardSensors as obs2 on obs2.id = (select max(id) from OnboardSensors as obs3 where obs3.id < obs1.id and obs1.tripId = :tripId) where obs1.gyroscopeX <> obs2.gyroscopeX and obs1.tripId = :tripId")
    suspend fun loadDecimated(tripId: Long): Array<OnboardSensors>

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Query("select obs1.* from OnboardSensors as obs1 left join OnboardSensors as obs2 on obs2.id = (select max(id) from OnboardSensors as obs3 where obs3.id < obs1.id and obs1.tripId = :tripId) where obs1.gyroscopeX <> obs2.gyroscopeX and obs1.tripId = :tripId")
    fun subscribeDecimated(tripId: Long): LiveData<Array<OnboardSensors>>
}