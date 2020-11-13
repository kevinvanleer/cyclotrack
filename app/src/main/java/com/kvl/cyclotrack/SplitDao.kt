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

    @Query("SELECT * FROM split WHERE tripId = :tripId")
    fun get(tripId: Long): LiveData<Array<Split>>

    @Query("SELECT * FROM split WHERE id = (SELECT max(id) FROM split WHERE tripId = :tripId)")
    fun getLast(tripId: Long): LiveData<Split>

}