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

    @Query("select * from split where round(totalDistance * :bucketFactor) = :bucket and distance > 0.99/:bucketFactor order by totalDistance/totalDuration desc limit :limit")
    suspend fun loadFastestDistance(bucket: Int, bucketFactor: Double, limit: Int): Array<Split>

    @Query("select * from split where round(totalDistance * :bucketFactor) = :bucket and distance > 0.99/:bucketFactor order by distance/duration desc limit :limit")
    suspend fun loadFastestSplit(bucket: Int, bucketFactor: Double, limit: Int): Array<Split>

    @Query("select * from split where round(totalDistance * :bucketFactor) = :bucket and distance > 0.99/:bucketFactor order by totalDistance/totalDuration desc limit :limit")
    fun subscribeFastestDistance(
        bucket: Int,
        bucketFactor: Double,
        limit: Int
    ): LiveData<Array<Split>>

    @Query("DELETE FROM split WHERE tripId = :tripId")
    suspend fun removeTripSplits(tripId: Long)

}
