package com.kvl.cyclotrack

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update

data class BikeId(val id: Long)

@Dao
interface BikeDao {
    @Insert
    fun save(bike: Bike): Long

    @Update
    fun update(vararg bikes: Bike)

    @Delete(entity = Bike::class)
    suspend fun removeBike(id: BikeId)

    @Delete(entity = Bike::class)
    suspend fun removeBikes(ids: Array<Bike>)

}
