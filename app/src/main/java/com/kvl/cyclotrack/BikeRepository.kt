package com.kvl.cyclotrack

import javax.inject.Inject

class BikeRepository @Inject constructor(private val bikeDao: BikeDao) {
    suspend fun add() = bikeDao.save(Bike())
    suspend fun delete(id: Long) = bikeDao.removeBike(BikeId(id))
    fun observe(id: Long) = bikeDao.subscribe(id)
    suspend fun get(id: Long) = bikeDao.get(id)
    fun observeAll() = bikeDao.subscribeAll()
    suspend fun update(vararg bikes: Bike) = bikeDao.update(*bikes)
    fun observeDefaultBike() = bikeDao.observeDefaultBike()
    suspend fun getDefaultBike() = bikeDao.getDefaultBike()
}