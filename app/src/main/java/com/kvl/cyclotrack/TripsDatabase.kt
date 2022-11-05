package com.kvl.cyclotrack

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kvl.cyclotrack.data.*

@Database(
    entities = [
        Trip::class,
        Measurements::class,
        TimeState::class,
        Split::class,
        OnboardSensors::class,
        Bike::class,
        ExternalSensor::class,
        Weather::class,
        HeartRateMeasurement::class,
        CadenceSpeedMeasurement::class
    ],
    version = 27
)
@TypeConverters(
    TimeStateEnumConverter::class,
    UserSexEnumConverter::class,
    GoogleFitSyncStatusConverter::class,
    SensorTypeConverter::class
)
abstract class TripsDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun measurementsDao(): MeasurementsDao
    abstract fun timeStateDao(): TimeStateDao
    abstract fun splitDao(): SplitDao
    abstract fun onboardSensorsDao(): OnboardSensorsDao
    abstract fun bikeDao(): BikeDao
    abstract fun externalSensorsDao(): ExternalSensorDao
    abstract fun weatherDao(): WeatherDao
    abstract fun heartRateMeasurementDao(): HeartRateMeasurementDao
    abstract fun cadenceSpeedMeasurementDao(): CadenceSpeedMeasurementDao
}
