package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.*

data class TripInProgress(
    val id: Long,
    val inProgress: Boolean,
)

data class TripId(
    val id: Long,
)

data class TripStuff(
    val id: Long,
    val name: String,
    val notes: String?,
    val userWheelCircumference: Float?,
)

data class TripWheelCircumference(
    val id: Long,
    val userWheelCircumference: Float?,
    val autoWheelCircumference: Float?,
)

data class TripStats(
    val id: Long,
    val distance: Double?,
    val duration: Double?,
    val averageSpeed: Float?,
    val userWheelCircumference: Float?,
    val autoWheelCircumference: Float?,
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

    @Update(entity = Trip::class)
    suspend fun updateCircumference(stats: TripWheelCircumference)

    @Update(entity = Trip::class)
    suspend fun updateStuff(stats: TripStuff)

    @Query("SELECT * FROM trip WHERE id = :tripId")
    fun subscribe(tripId: Long): LiveData<Trip>

    @Query("SELECT * FROM trip WHERE id = :tripId")
    suspend fun load(tripId: Long): Trip

    @Query("SELECT * from trip ORDER BY id DESC")
    fun loadAll(): LiveData<Array<Trip>>

    @Query("SELECT * from trip WHERE distance > 1 AND duration > 60 ORDER BY id DESC")
    fun getRealTrips(): LiveData<Array<Trip>>

    @Query("SELECT * from trip WHERE distance < 1 OR duration < 60")
    suspend fun getCleanupTrips(): Array<Trip>

    @Delete(entity = Trip::class)
    suspend fun removeTrip(id: TripId)

    @Delete(entity = Trip::class)
    suspend fun removeTrips(ids: Array<Trip>)
}
