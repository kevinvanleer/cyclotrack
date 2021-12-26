package com.kvl.cyclotrack

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Trip::class, Measurements::class, TimeState::class, Split::class, OnboardSensors::class, Bike::class],
    version = 19
)
@TypeConverters(TimeStateEnumConverter::class,
    UserSexEnumConverter::class,
    GoogleFitSyncStatusConverter::class)
abstract class TripsDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun measurementsDao(): MeasurementsDao
    abstract fun timeStateDao(): TimeStateDao
    abstract fun splitDao(): SplitDao
    abstract fun onboardSensorsDao(): OnboardSensorsDao
    abstract fun bikeDao(): BikeDao
}