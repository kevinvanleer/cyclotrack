package com.kvl.cyclotrack

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Trip::class, Measurements::class], version = 1)
abstract class TripsDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun measurementsDao(): MeasurementsDao
}