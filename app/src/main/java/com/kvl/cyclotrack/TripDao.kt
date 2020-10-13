package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

data class TripInProgress (
    val id: Long,
    val inProgress: Boolean,
)

data class TripStats (
    val id: Long,
    val distance: Double?,
    val duration: Double?,
    val averageSpeed: Float?,
)

@Dao
interface TripDao {
    @Insert()
    fun save(trip: Trip): Long

    @Update()
    fun update(vararg trips: Trip)

    @Update(entity = Trip::class)
    fun updateInProgress(inProgress: TripInProgress)

    @Update(entity = Trip::class)
    suspend fun updateStats(stats: TripStats)

    @Query("SELECT * FROM trip WHERE id = :tripId")
    fun load(tripId: Long): LiveData<Trip>

    @Query("SELECT * from trip")
    fun loadAll(): LiveData<Array<Trip>>

    @Query("SELECT * from trip WHERE distance > 0 AND duration > 0 ORDER BY id DESC")
    fun getRealTrips(): LiveData<Array<Trip>>
}