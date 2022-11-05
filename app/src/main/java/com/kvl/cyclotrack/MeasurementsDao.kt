package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface MeasurementsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(measurements: Measurements): Long

    @Update
    fun update(measurements: Measurements)

    @Query("SELECT * FROM measurements WHERE tripId = :tripId ORDER BY time ASC")
    suspend fun load(tripId: Long): Array<Measurements>

    @Query("SELECT * FROM measurements WHERE tripId = :tripId ORDER BY time DESC LIMIT :count")
    suspend fun loadLatestCount(tripId: Long, count: Int): Array<Measurements>

    @Query("SELECT * FROM measurements WHERE time = (SELECT max(time) FROM Measurements WHERE tripId = :tripId)")
    suspend fun loadLatest(tripId: Long): Measurements?

    @Query("SELECT * FROM measurements WHERE time = (SELECT max(time) FROM Measurements WHERE tripId = :tripId and accuracy < :accuracyThreshold)")
    suspend fun loadLatestAccurate(tripId: Long, accuracyThreshold: Float): Measurements?

    @Query("SELECT * FROM measurements WHERE tripId = :tripId ORDER BY time ASC")
    fun subscribe(tripId: Long): LiveData<Array<Measurements>>

    @Query("SELECT * FROM measurements WHERE time = (SELECT max(time) FROM measurements WHERE tripId = :tripId)")
    fun subscribeLatest(tripId: Long): LiveData<Measurements>

    @Query("UPDATE Measurements SET tripId = :newTripId WHERE tripId = :tripId")
    suspend fun changeTrip(tripId: Long, newTripId: Long)

    @Query(
        """
        SELECT gps.time,gps.speed,gps.latitude,gps.longitude,gps.altitude,gps.accuracy,gps.verticalAccuracyMeters,h.heartRate,c.lastEvent as cadenceLastEvent, c.revolutions as cadenceRevolutions, c.rpm as cadenceRpm,s.lastEvent as speedLastEvent, s.revolutions as speedRevolutions, s.rpm as speedRpm
        FROM measurements as gps
        INNER JOIN HeartRateMeasurement as h
        ON h.tripId = gps.tripId and (h.timestamp/1000) = (gps.time/1000)
        INNER JOIN CadenceSpeedMeasurement as c
        ON c.tripId = gps.tripId and (c.timestamp/1000) = (gps.time/1000) and c.sensorType = 1
        INNER JOIN CadenceSpeedMeasurement as s
        ON s.tripId = gps.tripId and (s.timestamp/1000) = (gps.time/1000) and s.sensorType = 2
        WHERE gps.tripId = :tripId ORDER BY time ASC
    """
    )
    suspend fun loadCritical(tripId: Long): Array<CriticalMeasurements>

    @Query(
        """
        SELECT gps.time,gps.speed,gps.latitude,gps.longitude,gps.altitude,gps.accuracy,gps.verticalAccuracyMeters,h.heartRate,c.lastEvent as cadenceLastEvent, c.revolutions as cadenceRevolutions, c.rpm as cadenceRpm,s.lastEvent as speedLastEvent, s.revolutions as speedRevolutions, s.rpm as speedRpm
        FROM measurements as gps
        INNER JOIN HeartRateMeasurement as h
        ON h.tripId = gps.tripId and (h.timestamp/1000) = (gps.time/1000)
        INNER JOIN CadenceSpeedMeasurement as c
        ON c.tripId = gps.tripId and (c.timestamp/1000) = (gps.time/1000) and c.sensorType = 1
        INNER JOIN CadenceSpeedMeasurement as s
        ON s.tripId = gps.tripId and (s.timestamp/1000) = (gps.time/1000) and s.sensorType = 2
        WHERE gps.tripId = :tripId ORDER BY time ASC
    """
    )
    fun subscribeCritical(tripId: Long): LiveData<Array<CriticalMeasurements>>
}
