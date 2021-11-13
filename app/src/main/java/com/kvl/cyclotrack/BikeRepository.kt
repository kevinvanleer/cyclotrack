package com.kvl.cyclotrack

import javax.inject.Inject

class BikeRepository @Inject constructor(private val bikeDao: BikeDao) {
    fun observe(id: Long) = bikeDao.subscribe(id)
    fun observeAll() = bikeDao.subscribeAll()
    fun update(vararg bikes: Bike) = bikeDao.update(*bikes)
}