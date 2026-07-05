package com.kvl.cyclotrack

import androidx.room.Dao
import androidx.room.Query

@Dao
interface OnboardSensorsDao {
    @Query("UPDATE OnboardSensors SET tripId = :newTripId WHERE tripId = :tripId")
    suspend fun changeTrip(tripId: Long, newTripId: Long)

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
