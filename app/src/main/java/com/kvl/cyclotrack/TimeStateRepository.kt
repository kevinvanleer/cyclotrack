package com.kvl.cyclotrack

import javax.inject.Inject

class TimeStateRepository @Inject constructor(private val timeStateDao: TimeStateDao) {
    fun observeTimeStates(tripId: Long) = timeStateDao.subscribe(tripId)

    suspend fun getTimeStates(tripId: Long) = timeStateDao.load(tripId)

    suspend fun appendTimeState(value: TimeState) = timeStateDao.save(value)

    fun update(timeState: TimeState) = timeStateDao.update(timeState)

    fun observeLatest(tripId: Long) = timeStateDao.subscribeCurrentState(tripId)

    suspend fun getLatest(tripId: Long) = timeStateDao.loadCurrentState(tripId)
}