package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import javax.inject.Inject

class SplitRepository @Inject constructor(private val splitDao: SplitDao) {
    fun getTripSplits(tripId: Long): LiveData<Array<Split>> = splitDao.get(tripId)
    fun getLastSplit(tripId: Long): LiveData<Split> = splitDao.getLast(tripId)
    suspend fun addSplit(split: Split) {
        splitDao.add(split)
    }

    suspend fun addSplits(splits: Array<Split>) {
        splitDao.add(splits)
    }

    suspend fun removeTripSplits(tripId: Long) {
        splitDao.removeTripSplits(tripId)
    }
}