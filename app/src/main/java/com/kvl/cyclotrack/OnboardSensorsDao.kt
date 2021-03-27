package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OnboardSensorsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(measurements: OnboardSensors): Long

    @Query("SELECT * FROM OnboardSensors WHERE tripId = :tripId")
    fun load(tripId: Long): LiveData<Array<OnboardSensors>>
}