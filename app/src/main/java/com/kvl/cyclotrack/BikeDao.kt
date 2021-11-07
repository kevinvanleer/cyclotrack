package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.*

data class BikeId(val id: Long)

@Dao
interface BikeDao {
    @Insert
    fun save(bike: Bike): Long

    @Update
    fun update(vararg bikes: Bike)

    @Query("SELECT * FROM Bike WHERE id = :bikeId")
    fun subscribe(bikeId: Long): LiveData<Bike>

    @Delete(entity = Bike::class)
    suspend fun removeBike(id: BikeId)

    @Delete(entity = Bike::class)
    suspend fun removeBikes(ids: Array<Bike>)

}
