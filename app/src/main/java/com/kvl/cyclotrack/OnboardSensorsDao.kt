package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomWarnings
import androidx.room.Update

@Dao
interface OnboardSensorsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(measurements: OnboardSensors): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(measurements: List<OnboardSensors>)

    @Update
    fun update(measurements: OnboardSensors)

    @Query("UPDATE OnboardSensors SET tripId = :newTripId WHERE tripId = :tripId")
    suspend fun changeTrip(tripId: Long, newTripId: Long)

    @Query("SELECT * FROM OnboardSensors WHERE tripId = :tripId")
    suspend fun load(tripId: Long): Array<OnboardSensors>

    @Query("SELECT * FROM OnboardSensors WHERE tripId = :tripId")
    fun subscribe(tripId: Long): LiveData<Array<OnboardSensors>>

    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("select obs1.* from OnboardSensors as obs1 left join OnboardSensors as obs2 on obs2.id = (select max(id) from OnboardSensors as obs3 where obs3.id < obs1.id and obs1.tripId = :tripId) where obs1.gyroscopeX <> obs2.gyroscopeX and obs1.tripId = :tripId")
    suspend fun loadDecimated(tripId: Long): Array<OnboardSensors>

    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("select obs1.* from OnboardSensors as obs1 left join OnboardSensors as obs2 on obs2.id = (select max(id) from OnboardSensors as obs3 where obs3.id < obs1.id and obs1.tripId = :tripId) where obs1.gyroscopeX <> obs2.gyroscopeX and obs1.tripId = :tripId")
    fun subscribeDecimated(tripId: Long): LiveData<Array<OnboardSensors>>

    @Query("SELECT DISTINCT tripId FROM OnboardSensors ORDER BY tripId DESC")
    suspend fun tripIds(): List<Long>

    @Query("SELECT count(*) FROM Trip WHERE EXISTS(SELECT 1 FROM OnboardSensors o WHERE o.tripId = Trip.id) OR EXISTS(SELECT 1 FROM OnboardSensorMeasurement m WHERE m.tripId = Trip.id)")
    suspend fun sensorTripCount(): Int

    @Query("SELECT count(*) FROM Trip WHERE NOT EXISTS(SELECT 1 FROM OnboardSensors o WHERE o.tripId = Trip.id) AND EXISTS(SELECT 1 FROM OnboardSensorMeasurement m WHERE m.tripId = Trip.id)")
    suspend fun migratedTripCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM OnboardSensors)")
    suspend fun hasRows(): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM Trip WHERE id = :tripId)")
    suspend fun tripExists(tripId: Long): Boolean

    @Query("SELECT * FROM OnboardSensors WHERE tripId = :tripId AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun loadChunk(tripId: Long, afterId: Long, limit: Int): List<OnboardSensors>

    @Query("DELETE FROM OnboardSensors WHERE tripId = :tripId AND id <= :maxId")
    suspend fun deleteChunk(tripId: Long, maxId: Long)
}
