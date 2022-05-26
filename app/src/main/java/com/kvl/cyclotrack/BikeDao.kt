package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.*

data class BikeId(val id: Long)

@Dao
interface BikeDao {
    @Insert
    suspend fun save(bike: Bike): Long

    @Update
    suspend fun update(vararg bikes: Bike)

    @Query("SELECT * FROM Bike WHERE id = :bikeId")
    suspend fun get(bikeId: Long): Bike?

    @Query("SELECT * FROM Bike WHERE isDefault = 1")
    suspend fun getDefaultBike(): Bike

    @Query("SELECT * FROM Bike WHERE isDefault = 1")
    fun observeDefaultBike(): LiveData<Bike>

    @Query("SELECT * FROM Bike WHERE id = :bikeId")
    fun subscribe(bikeId: Long): LiveData<Bike>

    @Query("SELECT * FROM Bike")
    fun subscribeAll(): LiveData<Array<Bike>>

    @Delete(entity = Bike::class)
    suspend fun removeBike(id: BikeId)

    @Delete(entity = Bike::class)
    suspend fun removeBikes(ids: Array<Bike>)

}
