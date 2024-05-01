package com.kvl.cyclotrack.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kvl.cyclotrack.TimeStateEnum

const val SPEED = 1
const val CADENCE = 2

data class AutoTimeState(
    val timeState: TimeStateEnum,
    val timestamp: Long,
    val triggered: Boolean,
    val sensorType: SensorType
)

@Dao
interface CadenceSpeedMeasurementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(measurements: CadenceSpeedMeasurement): Long

    @Update
    fun update(measurements: CadenceSpeedMeasurement)

    @Query("SELECT * FROM CadenceSpeedMeasurement WHERE tripId = :tripId and sensorType = $SPEED ORDER BY timestamp ASC")
    suspend fun loadSpeed(tripId: Long): Array<CadenceSpeedMeasurement>

    @Query("SELECT * FROM CadenceSpeedMeasurement WHERE tripId = :tripId and sensorType = $SPEED ORDER BY timestamp ASC")
    fun subscribeSpeed(tripId: Long): LiveData<Array<CadenceSpeedMeasurement>>

    @Query("SELECT * FROM CadenceSpeedMeasurement WHERE timestamp = (SELECT max(timestamp) FROM CadenceSpeedMeasurement WHERE tripId = :tripId and sensorType = $SPEED) ORDER BY timestamp ASC")
    fun subscribeLatestSpeed(tripId: Long): LiveData<Array<CadenceSpeedMeasurement>>

    @Query("SELECT * FROM CadenceSpeedMeasurement WHERE tripId = :tripId and sensorType = $CADENCE ORDER BY timestamp ASC")
    suspend fun loadCadence(tripId: Long): Array<CadenceSpeedMeasurement>

    @Query("SELECT * FROM CadenceSpeedMeasurement WHERE tripId = :tripId and sensorType = $CADENCE ORDER BY timestamp ASC")
    fun subscribeCadence(tripId: Long): LiveData<Array<CadenceSpeedMeasurement>>

    @Query("SELECT * FROM CadenceSpeedMeasurement WHERE timestamp = (SELECT max(timestamp) FROM CadenceSpeedMeasurement WHERE tripId = :tripId and sensorType = $CADENCE) ORDER BY timestamp ASC")
    fun subscribeLatestCadence(tripId: Long): LiveData<Array<CadenceSpeedMeasurement>>

    @Query("UPDATE CadenceSpeedMeasurement SET tripId = :newTripId WHERE tripId = :tripId")
    suspend fun changeTrip(tripId: Long, newTripId: Long)

    @Query(
        """
        select 
            case 
                when csm.rpm >= :rpmThreshold then 2
                when csm.rpm < :rpmThreshold then 1
            end as timeState, 
            max(csm.timestamp) as timestamp,
            case
                when csm.rpm >= :rpmThreshold then :referenceTime - max(csm.timestamp) > :pauseThreshold
                when csm.rpm < :rpmThreshold then :referenceTime - max(csm.timestamp) > :resumeThreshold
            end as triggered,
            csm.sensorType
        from CadenceSpeedMeasurement as csm
        where csm.tripId = :tripId and csm.timestamp < :referenceTime and csm.sensorType = 1
        group by csm.rpm < :rpmThreshold
    """
    )
    suspend fun getAutoTimeStates(
        tripId: Long,
        referenceTime: Long = System.currentTimeMillis(),
        pauseThreshold: Long = 5000,
        resumeThreshold: Long = 5000,
        rpmThreshold: Float = 50f
    ): Array<AutoTimeState>
}
