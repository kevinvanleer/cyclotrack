package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import javax.inject.Inject

class TimeStateRepository @Inject constructor(private val timeStateDao: TimeStateDao) {
    fun getTimeStates(tripId: Long): LiveData<Array<TimeState>> {
        return timeStateDao.load(tripId)
    }

    suspend fun appendTimeState(value: TimeState): Long {
        return timeStateDao.save(value)
    }

    fun getLatest(tripId: Long): LiveData<TimeState> {
        return timeStateDao.getCurrentState(tripId)
    }
}