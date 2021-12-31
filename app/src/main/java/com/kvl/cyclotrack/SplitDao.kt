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

    @Query("select * from split where totalDistance >= :distanceLowerBound and totalDistance < :distanceUpperBound order by totalDuration asc limit :limit")
    fun fastestDistances(
        distanceLowerBound: Double,
        distanceUpperBound: Double,
        limit: Int
    ): LiveData<Split>

    @Query("select *, totalDistance * 0.000621371 as miles, totalDistance/totalDuration as speed from split where round(miles) = :distance group by tripId order by speed desc limit :limit")
    fun fastestDistance(distance: Int, limit: Int): LiveData<Split>

    @Query("DELETE FROM split WHERE tripId = :tripId")
    suspend fun removeTripSplits(tripId: Long)

}