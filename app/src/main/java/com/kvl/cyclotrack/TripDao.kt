package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.*

data class TripInProgress(
    val id: Long,
    val inProgress: Boolean,
)

data class TripStats(
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

    @Query("SELECT * from trip ORDER BY id DESC")
    fun loadAll(): LiveData<Array<Trip>>

    @Query("SELECT * from trip WHERE distance > 1 AND duration > 60 ORDER BY id DESC")
    fun getRealTrips(): LiveData<Array<Trip>>

    @Query("SELECT * from trip WHERE distance < 1 OR duration < 60")
    suspend fun getCleanupTrips(): Array<Trip>

    @Delete(entity = Trip::class)
    suspend fun removeTrip(id: Trip)

    @Delete(entity = Trip::class)
    suspend fun removeTrips(ids: Array<Trip>)
}
