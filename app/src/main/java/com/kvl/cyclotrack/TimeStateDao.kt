package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TimeStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(timeState: TimeState): Long

    @Update
    fun update(timeState: TimeState)

    @Query("SELECT * FROM timeState WHERE tripId = :tripId order by timestamp asc")
    suspend fun load(tripId: Long): Array<TimeState>

    @Query("SELECT * FROM timeState WHERE tripId = :tripId order by timestamp asc")
    fun subscribe(tripId: Long): LiveData<Array<TimeState>>

    @Query("SELECT * FROM timeState WHERE timestamp = (SELECT max(timestamp) FROM timeState WHERE tripId = :tripId)")
    fun subscribeCurrentState(tripId: Long): LiveData<TimeState>

    @Query("SELECT * FROM timeState WHERE timestamp = (SELECT max(timestamp) FROM timeState WHERE tripId = :tripId)")
    suspend fun loadCurrentState(tripId: Long): TimeState
}
