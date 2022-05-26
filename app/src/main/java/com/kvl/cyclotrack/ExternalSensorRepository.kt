package com.kvl.cyclotrack

import javax.inject.Inject

class ExternalSensorRepository @Inject constructor(private val externalSensorDao: ExternalSensorDao) {
    fun bikeSensors(bikeId: Long) = externalSensorDao.subscribeBike(bikeId)
    fun bodySensors() = externalSensorDao.subscribeNoBike()
    suspend fun all() = externalSensorDao.all()
    fun observeAll() = externalSensorDao.subscribeAll()
    suspend fun addSensor(newSensor: ExternalSensor) = externalSensorDao.save(newSensor)
    suspend fun removeSensor(sensor: ExternalSensor) = externalSensorDao.remove(sensor)
    suspend fun get(address: String) = externalSensorDao.get(address)
    suspend fun update(vararg sensors: ExternalSensor) = externalSensorDao.update(*sensors)
}
