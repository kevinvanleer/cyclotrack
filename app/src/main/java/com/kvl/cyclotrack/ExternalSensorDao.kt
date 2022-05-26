package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.*

data class ExternalSensorId(val id: Long)

@Dao
interface ExternalSensorDao {
    @Insert
    suspend fun save(sensor: ExternalSensor): Long

    @Update
    suspend fun update(vararg sensors: ExternalSensor)

    @Query("SELECT * FROM ExternalSensor WHERE id = :sensorId")
    suspend fun get(sensorId: Long): ExternalSensor?

    @Query("SELECT * FROM ExternalSensor WHERE address = :address")
    suspend fun get(address: String): ExternalSensor?

    @Query("SELECT * FROM ExternalSensor")
    suspend fun all(): Array<ExternalSensor>

    @Query("SELECT * FROM ExternalSensor WHERE id = :sensorId")
    fun subscribe(sensorId: Long): LiveData<ExternalSensor>

    @Query("SELECT * FROM ExternalSensor")
    fun subscribeAll(): LiveData<Array<ExternalSensor>>

    @Query("SELECT * FROM ExternalSensor WHERE bikeId = :bikeId")
    fun subscribeBike(bikeId: Long): LiveData<Array<ExternalSensor>>

    @Query("SELECT * FROM ExternalSensor WHERE bikeId IS NULL")
    fun subscribeNoBike(): LiveData<Array<ExternalSensor>>

    @Delete(entity = ExternalSensor::class)
    suspend fun remove(sensor: ExternalSensor)

    @Delete(entity = ExternalSensor::class)
    suspend fun remove(id: ExternalSensorId)

    @Delete(entity = ExternalSensor::class)
    suspend fun remove(sensors: Array<ExternalSensor>)
}
