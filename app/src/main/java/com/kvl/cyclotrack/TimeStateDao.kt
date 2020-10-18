package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TimeStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(timeState: TimeState): Long

    @Query("SELECT * FROM timeState WHERE tripId = :tripId")
    fun load(tripId: Long): LiveData<Array<TimeState>>

    @Query("SELECT * FROM timeState WHERE id = (SELECT max(id) FROM timeState WHERE tripId = :tripId)")
    fun getCurrentState(tripId: Long): LiveData<TimeState>
}
