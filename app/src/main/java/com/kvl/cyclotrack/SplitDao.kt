package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SplitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(split: Split): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(splits: Array<Split>)

    @Query("SELECT * FROM split WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun load(tripId: Long): Array<Split>

    @Query("SELECT * FROM split WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun subscribe(tripId: Long): LiveData<Array<Split>>

    @Query("SELECT * FROM split WHERE timestamp = (SELECT max(timestamp) FROM split WHERE tripId = :tripId)")
    fun subscribeLast(tripId: Long): LiveData<Split>

    @Query("DELETE FROM split WHERE tripId = :tripId")
    suspend fun removeTripSplits(tripId: Long)

}