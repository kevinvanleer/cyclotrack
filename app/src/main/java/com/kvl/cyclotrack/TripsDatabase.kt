package com.kvl.cyclotrack

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Trip::class, Measurements::class, TimeState::class, Split::class],
    version = 7)
@TypeConverters(TimeStateEnumConverter::class)
abstract class TripsDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun measurementsDao(): MeasurementsDao
    abstract fun timeStateDao(): TimeStateDao
    abstract fun splitDao(): SplitDao
}