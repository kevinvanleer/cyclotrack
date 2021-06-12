package com.kvl.cyclotrack

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE `TimeState` (`tripId` INTEGER NOT NULL, `state` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY, FOREIGN KEY(`tripId`) REFERENCES Trip(`id`) ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX index_TimeState_tripId on TimeState(`tripId`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE `Split` (`tripId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `distance` REAL NOT NULL, `id` INTEGER PRIMARY KEY, FOREIGN KEY(`tripId`) REFERENCES Trip(`id`) ON DELETE NO ACTION)")
        database.execSQL("CREATE INDEX index_Split_tripId on Split(`tripId`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE `Split`")
        database.execSQL("CREATE TABLE `Split` (`tripId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `distance` REAL NOT NULL, `totalDistance` REAL NOT NULL, `duration` REAL NOT NULL, `totalDuration` REAL NOT NULL, `id` INTEGER PRIMARY KEY, FOREIGN KEY(`tripId`) REFERENCES Trip(`id`) ON DELETE NO ACTION)")
        database.execSQL("CREATE INDEX index_Split_tripId on Split(`tripId`)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `Split` RENAME TO `Split_4_5`")
        database.execSQL("CREATE TABLE `Split` (`tripId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `distance` REAL NOT NULL, `totalDistance` REAL NOT NULL, `duration` REAL NOT NULL, `totalDuration` REAL NOT NULL, `id` INTEGER PRIMARY KEY, FOREIGN KEY(`tripId`) REFERENCES Trip(`id`) ON DELETE CASCADE)")
        database.execSQL("INSERT INTO `Split` SELECT * FROM `Split_4_5`")
        database.execSQL("DROP TABLE `Split_4_5`")
        database.execSQL("CREATE INDEX index_Split_tripId on Split(`tripId`)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN notes text")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `Measurements` ADD COLUMN `heartRate` INTEGER")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `Measurements` ADD COLUMN `speedRevolutions` INTEGER")
        database.execSQL("ALTER TABLE `Measurements` ADD COLUMN `speedLastEvent` INTEGER")
        database.execSQL("ALTER TABLE `Measurements` ADD COLUMN `speedRpm` FLOAT")
        database.execSQL("ALTER TABLE `Measurements` ADD COLUMN `cadenceRevolutions` INTEGER")
        database.execSQL("ALTER TABLE `Measurements` ADD COLUMN `cadenceLastEvent` INTEGER")
        database.execSQL("ALTER TABLE `Measurements` ADD COLUMN `cadenceRpm` FLOAT")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN `userWheelCircumference` FLOAT")
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN `autoWheelCircumference` FLOAT")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN `userSex` INTEGER")
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN `userWeight` FLOAT")
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN `userHeight` FLOAT")
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN `userAge` FLOAT")
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN `userVo2max` FLOAT")
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN `userRestingHeartRate` INTEGER")
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN `userMaxHeartRate` INTEGER")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE `OnboardSensors` (`tripId` INTEGER NOT NULL,  `timestamp` INTEGER NOT NULL, `accelerometerX` FLOAT,`accelerometerY` FLOAT,`accelerometerZ` FLOAT, `accelerometerAverageX` FLOAT,`accelerometerAverageY` FLOAT,`accelerometerAverageZ` FLOAT, `gyroscopeX` FLOAT,`gyroscopeY` FLOAT,`gyroscopeZ` FLOAT, `gyroscopeAverageX` FLOAT,`gyroscopeAverageY` FLOAT,`gyroscopeAverageZ` FLOAT,`tiltX` FLOAT,`tiltY` FLOAT,`tiltZ` FLOAT,`id` INTEGER PRIMARY KEY, FOREIGN KEY(`tripId`) REFERENCES Trip(`id`) ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX index_OnboardSensors_tripId on OnboardSensors(`tripId`)")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE `OnboardSensors`")
        database.execSQL("CREATE TABLE `OnboardSensors` (`tripId` INTEGER NOT NULL,  `timestamp` integer not null, `gravityX` FLOAT,`gravityY` FLOAT,`gravityZ` FLOAT, `gyroscopeX` FLOAT,`gyroscopeY` FLOAT,`gyroscopeZ` FLOAT, `id` INTEGER PRIMARY KEY, FOREIGN KEY(`tripId`) REFERENCES Trip(`id`) ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX index_OnboardSensors_tripId on OnboardSensors(`tripId`)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `measurements_schema12_to_13`(`tripId` INTEGER NOT NULL, `accuracy` REAL NOT NULL, `altitude` REAL NOT NULL, `bearing` REAL NOT NULL, `elapsedRealtimeNanos` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `speed` REAL NOT NULL, `time` INTEGER NOT NULL, `bearingAccuracyDegrees` REAL NOT NULL, `elapsedRealtimeUncertaintyNanos` REAL NOT NULL, `speedAccuracyMetersPerSecond` REAL NOT NULL, `verticalAccuracyMeters` REAL NOT NULL, `heartRate` INTEGER, `cadenceRevolutions` INTEGER, `cadenceLastEvent` INTEGER, `cadenceRpm` REAL, `speedRevolutions` INTEGER, `speedLastEvent` INTEGER, `speedRpm` REAL, `id` INTEGER PRIMARY KEY, FOREIGN KEY(`tripId`) REFERENCES Trip(`id`) ON DELETE CASCADE)")
        database.execSQL("INSERT INTO `measurements_schema12_to_13`(tripId, accuracy, altitude, bearing, elapsedRealtimeNanos, latitude, longitude, speed, time, bearingAccuracyDegrees, elapsedRealtimeUncertaintyNanos, speedAccuracyMetersPerSecond, verticalAccuracyMeters, heartRate, cadenceRevolutions, cadenceLastEvent, cadenceRpm, speedRevolutions, speedLastEvent, speedRpm, id) SELECT tripId, accuracy, altitude, bearing, elapsedRealtimeNanos, latitude, longitude, speed, time, bearingAccuracyDegrees, elapsedRealtimeUncertaintyNanos, speedAccuracyMetersPerSecond, verticalAccuracyMetersPerSecond, heartRate, cadenceRevolutions, cadenceLastEvent, cadenceRpm, speedRevolutions, speedLastEvent, speedRpm, id FROM Measurements")
        database.execSQL("DROP TABLE `Measurements`")
        database.execSQL("ALTER TABLE `measurements_schema12_to_13` RENAME TO `Measurements`")
        database.execSQL("CREATE INDEX index_Measurements_tripId on Measurements(`tripId`)")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `TimeState` ADD COLUMN `originalTripId` INTEGER")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("UPDATE Trip SET inProgress = 0 WHERE inProgress = 1")
        database.execSQL("INSERT INTO `TimeState` (`tripId`, `state`, `timestamp`, `originalTripId`) SELECT `tripId`, 3, `timestamp`+1, `originalTripId` FROM TimeState GROUP BY `tripId` HAVING max(timestamp) and `state` != 3")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object TripsDatabaseModule {

    @Provides
    @Singleton
    fun provideTripsDatabase(@ApplicationContext appContext: Context): TripsDatabase =
        Room.databaseBuilder(appContext, TripsDatabase::class.java, "trips-cyclotrack-kvl")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
            ).build()

    @Provides
    @Singleton
    fun provideTripDao(db: TripsDatabase): TripDao {
        return db.tripDao()
    }

    @Provides
    @Singleton
    fun provideMeasurementsDao(db: TripsDatabase): MeasurementsDao {
        return db.measurementsDao()
    }

    @Provides
    @Singleton
    fun provideTimeStateDao(db: TripsDatabase): TimeStateDao {
        return db.timeStateDao()
    }

    @Provides
    @Singleton
    fun provideSplitDao(db: TripsDatabase): SplitDao {
        return db.splitDao()
    }

    @Provides
    @Singleton
    fun provideOnboardSensorsDao(db: TripsDatabase): OnboardSensorsDao {
        return db.onboardSensorsDao()
    }
}