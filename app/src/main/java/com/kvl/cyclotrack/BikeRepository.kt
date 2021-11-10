package com.kvl.cyclotrack.repos

import com.kvl.cyclotrack.BikeDao
import javax.inject.Inject

class BikeRepository @Inject constructor(private val bikeDao: BikeDao) {
    fun observe(id: Long) = bikeDao.subscribe(id)
    fun observeAll() = bikeDao.subscribeAll()
}