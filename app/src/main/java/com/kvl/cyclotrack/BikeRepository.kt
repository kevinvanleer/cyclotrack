package com.kvl.cyclotrack

import javax.inject.Inject

class BikeRepository @Inject constructor(private val bikeDao: BikeDao) {
    fun observe(id: Long) = bikeDao.subscribe(id)
    fun get(id: Long) = bikeDao.get(id)
    fun observeAll() = bikeDao.subscribeAll()
    fun update(vararg bikes: Bike) = bikeDao.update(*bikes)
}