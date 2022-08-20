package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import javax.inject.Inject

class SplitRepository @Inject constructor(private val splitDao: SplitDao) {
    fun observeTripSplits(tripId: Long): LiveData<Array<Split>> = splitDao.subscribe(tripId)

    fun observeLastSplit(tripId: Long): LiveData<Split> = splitDao.subscribeLast(tripId)

    suspend fun getTripSplits(tripId: Long): Array<Split> = splitDao.load(tripId)

    suspend fun addSplit(split: Split) = splitDao.add(split)

    suspend fun addSplits(splits: Array<Split>) = splitDao.add(splits)

    suspend fun removeTripSplits(tripId: Long) = splitDao.removeTripSplits(tripId)

    fun observeFastestDistances(distance: Double, limit: Int = 10) = splitDao.fastestDistances(
        distanceLowerBound = kotlin.math.floor(distance),
        distanceUpperBound = distance + 20,
        limit = limit
    )

    suspend fun getFastestDistance(distance: Int, conversionFactor: Double, limit: Int = 10) =
        splitDao.loadFastestDistance(
            bucket = distance,
            bucketFactor = conversionFactor,
            limit = limit
        )

    fun observeFastestDistance(distance: Int, conversionFactor: Double, limit: Int = 10) =
        splitDao.subscribeFastestDistance(
            bucket = distance,
            bucketFactor = conversionFactor,
            limit = limit
        )
}
