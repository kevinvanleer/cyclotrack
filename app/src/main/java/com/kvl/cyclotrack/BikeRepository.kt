package com.kvl.cyclotrack.repos

import androidx.lifecycle.LiveData
import com.kvl.cyclotrack.model.Bike
import com.kvl.cyclotrack.model.BikeDao
import javax.inject.Inject

class BikeRepository @Inject constructor(private val bikeDao: BikeDao) {
    fun observe(id: Long): LiveData<Bike> = bikeDao.subscribe(id)
}