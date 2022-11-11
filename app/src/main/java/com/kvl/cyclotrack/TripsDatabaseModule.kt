package com.kvl.cyclotrack

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.room.OnConflictStrategy
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.kvl.cyclotrack.data.CadenceSpeedMeasurementDao
import com.kvl.cyclotrack.data.HeartRateMeasurementDao
import com.kvl.cyclotrack.data.SensorType
import com.kvl.cyclotrack.events.DatabaseOpen
import com.kvl.cyclotrack.events.PostMigration
import com.kvl.cyclotrack.events.PreMigration
import com.kvl.cyclotrack.util.getBikeMassOrNull
import com.kvl.cyclotrack.util.getPreferences
import com.kvl.cyclotrack.util.getUserCircumferenceOrNull
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.greenrobot.eventbus.EventBus
import javax.inject.Singleton

fun resetFailedGoogleFitSyncs(database: SupportSQLiteDatabase) {
    database.execSQL("UPDATE Trip SET googleFitSyncStatus = 0 WHERE googleFitSyncStatus = 2")
}

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

val MIGRATION_10_12 = object : Migration(10, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
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

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN `googleFitSyncStatus` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        resetFailedGoogleFitSyncs(database)
    }

}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `OnboardSensors` ADD COLUMN `pressure` FLOAT")
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `Bike` (`name` TEXT, `dateOfPurchase` INTEGER, `weight` REAL, `wheelCircumference` REAL, `isDefault` INTEGER NOT NULL DEFAULT 0, `id` INTEGER PRIMARY KEY AUTOINCREMENT)")
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trigger_bike_new_default_insert
            AFTER INSERT ON Bike
            FOR EACH ROW
            WHEN NEW.isDefault = 1 AND (SELECT sum(isDefault) FROM Bike) > 1
            BEGIN 
                UPDATE Bike SET isDefault = 0 WHERE id != NEW.id and isDefault = 1;
            END
            """
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trigger_bike_new_default_update 
            AFTER UPDATE OF `isDefault` ON Bike
            FOR EACH ROW
            WHEN NEW.isDefault = 1 AND OLD.isDefault = 0 AND (SELECT sum(isDefault) FROM Bike) > 1
            BEGIN 
                UPDATE Bike SET isDefault = 0 WHERE id != NEW.id and isDefault = 1;
            END
            """
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trigger_bike_remove_default_delete
            AFTER DELETE ON Bike
            FOR EACH ROW
            WHEN OLD.isDefault = 1
            BEGIN 
                UPDATE Bike SET isDefault = 1 WHERE id = (SELECT min(id) FROM Bike);
            END
            """
        )
        database.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trigger_bike_remove_default_update 
            AFTER UPDATE OF `isDefault` ON Bike
            FOR EACH ROW
            WHEN NEW.isDefault = 0 AND OLD.isDefault = 1 AND (SELECT sum(isDefault) FROM Bike) = 0
            BEGIN 
                UPDATE Bike SET isDefault = 1 WHERE id = (SELECT min(id) FROM Bike) AND (SELECT sum(isDefault) FROM Bike) = 0;
            END
            """
        )
        database.insert("Bike", OnConflictStrategy.ABORT, ContentValues().apply {
            put("weight", getBikeMassOrNull(CyclotrackApp.instance))
            put("wheelCircumference", getUserCircumferenceOrNull(CyclotrackApp.instance))
            put("isDefault", 1)
        })
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN `bikeId` INTEGER NOT NULL DEFAULT 1 REFERENCES Bike(id) ON DELETE SET DEFAULT")
        database.execSQL("CREATE INDEX index_Trip_bikeId on Trip(`bikeId`)")
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `ExternalSensor` (`name` TEXT, `address` TEXT NOT NULL, `features` INTEGER, `bikeId` INTEGER, `id` INTEGER PRIMARY KEY AUTOINCREMENT, FOREIGN KEY(`bikeId`) REFERENCES Bike(`id`) ON DELETE SET DEFAULT)")
        val bikeId = database.query("SELECT id FROM Bike WHERE isDefault = 1").let {
            it.isBeforeFirst && it.moveToNext()
            when (it.count) {
                1 -> it.getLong(0)
                else -> 1
            }
        }
        CyclotrackApp.instance.let { context ->
            getPreferences(context).getStringSet(
                context.resources.getString(R.string.preferences_paired_ble_devices_key),
                HashSet()
            )?.forEach {
                try {
                    Gson().fromJson(it, ExternalSensor::class.java).let {
                        database.insert(
                            "ExternalSensor",
                            OnConflictStrategy.ABORT,
                            ContentValues().apply {
                                put("address", it.address)
                                put("name", it.name)
                                put("bikeId", bikeId)
                            })
                    }
                } catch (e: JsonSyntaxException) {
                    Log.e("Migration 19->20", "Could not parse sensor from JSON", e)
                    ExternalSensor("REMOVE_INVALID_SENSOR")
                }
            }
        }

        database.execSQL("CREATE INDEX index_ExternalSensor_bikeId on ExternalSensor(`bikeId`)")
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        resetFailedGoogleFitSyncs(database)
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE `Weather` (`id` INTEGER PRIMARY KEY, `tripId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `sunrise` INTEGER NOT NULL, `sunset` INTEGER NOT NULL, `temperature` REAL NOT NULL, `feelsLike` REAL NOT NULL, `pressure` INTEGER NOT NULL, `humidity` INTEGER NOT NULL, dewPoint REAL NOT NULL, `uvIndex` REAL NOT NULL, `clouds` INTEGER NOT NULL, `visibility` INTEGER NOT NULL, `windSpeed` REAL NOT NULL, windDirection INTEGER NOT NULL, FOREIGN KEY(`tripId`) REFERENCES Trip(`id`) ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX index_Weather_tripId on Weather(`tripId`)")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `Trip` ADD COLUMN `stravaSyncStatus` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE `CadenceSpeedMeasurement` (`id` INTEGER PRIMARY KEY, `tripId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `sensorType` INTEGER NOT NULL, `revolutions` INTEGER NOT NULL, lastEvent INTEGER NOT NULL, rpm REAL, FOREIGN KEY(`tripId`) REFERENCES Trip(`id`) ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX index_CadenceSpeedMeasurement_tripId on CadenceSpeedMeasurement(`tripId`)")
        database.execSQL("CREATE TABLE `HeartRateMeasurement` (`id` INTEGER PRIMARY KEY, `tripId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `heartRate` INTEGER NOT NULL, energyExpended INTEGER, rrIntervals TEXT, FOREIGN KEY(`tripId`) REFERENCES Trip(`id`) ON DELETE CASCADE)")
        database.execSQL("CREATE INDEX index_HeartRateMeasurement_tripId on HeartRateMeasurement(`tripId`)")
    }
}

val MIGRATION_24_23 = object : Migration(24, 23) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE `CadenceSpeedMeasurement`")
        database.execSQL("DROP TABLE `HeartRateMeasurement`")
    }
}

val MIGRATION_25_24 = object : Migration(25, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DELETE FROM `CadenceSpeedMeasurement`")
        database.execSQL("DELETE FROM `HeartRateMeasurement`")
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """INSERT INTO `HeartRateMeasurement`
                    (tripId, 
                    timestamp, 
                    heartRate)
                SELECT 
                    tripId,
                    time,
                    heartRate
                FROM Measurements
                WHERE heartRate IS NOT NULL"""
        )
        database.execSQL(
            """INSERT INTO `CadenceSpeedMeasurement`
                    (tripId, 
                    timestamp, 
                    sensorType,
                    revolutions,
                    lastEvent,
                    rpm)
                SELECT 
                    tripId,
                    time,
                    ?,
                    cadenceRevolutions,
                    cadenceLastEvent,
                    cadenceRpm 
                FROM Measurements
                WHERE cadenceRevolutions IS NOT NULL""",
            arrayOf(SensorType.CADENCE.value)
        )
        database.execSQL(
            """INSERT INTO `CadenceSpeedMeasurement`
                    (tripId, 
                    timestamp, 
                    sensorType,
                    revolutions,
                    lastEvent,
                    rpm) 
                SELECT 
                    tripId,
                    time,
                    ?,
                    speedRevolutions, 
                    speedLastEvent, 
                    speedRpm
                FROM Measurements
                WHERE speedRevolutions IS NOT NULL""",
            arrayOf(SensorType.SPEED.value)
        )
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `measurements_schema25_to_26`(`tripId` INTEGER NOT NULL, `accuracy` REAL NOT NULL, `altitude` REAL NOT NULL, `bearing` REAL NOT NULL, `elapsedRealtimeNanos` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `speed` REAL NOT NULL, `time` INTEGER NOT NULL, `bearingAccuracyDegrees` REAL NOT NULL, `elapsedRealtimeUncertaintyNanos` REAL NOT NULL, `speedAccuracyMetersPerSecond` REAL NOT NULL, `verticalAccuracyMeters` REAL NOT NULL, `id` INTEGER PRIMARY KEY, FOREIGN KEY(`tripId`) REFERENCES Trip(`id`) ON DELETE CASCADE)")
        database.execSQL("INSERT INTO `measurements_schema25_to_26`(tripId, accuracy, altitude, bearing, elapsedRealtimeNanos, latitude, longitude, speed, time, bearingAccuracyDegrees, elapsedRealtimeUncertaintyNanos, speedAccuracyMetersPerSecond, verticalAccuracyMeters, id) SELECT tripId, accuracy, altitude, bearing, elapsedRealtimeNanos, latitude, longitude, speed, time, bearingAccuracyDegrees, elapsedRealtimeUncertaintyNanos, speedAccuracyMetersPerSecond, verticalAccuracyMeters, id FROM Measurements")
        database.execSQL("DROP TABLE `Measurements`")
        database.execSQL("ALTER TABLE `measurements_schema25_to_26` RENAME TO `Measurements`")
        database.execSQL("CREATE INDEX index_Measurements_tripId on Measurements(`tripId`)")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `TimeState` ADD COLUMN auto INTEGER NOT NULL DEFAULT 0")
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
                MIGRATION_10_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
                MIGRATION_18_19,
                MIGRATION_19_20,
                MIGRATION_20_21,
                MIGRATION_21_22,
                MIGRATION_22_23,
                MIGRATION_23_24,
                MIGRATION_24_23,
                MIGRATION_25_24,
                MIGRATION_24_25,
                MIGRATION_25_26,
                MIGRATION_26_27,
            )
            .openHelperFactory { configuration ->
                FrameworkSQLiteOpenHelperFactory().create(
                    SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                        .name(configuration.name)
                        .callback(object :
                            SupportSQLiteOpenHelper.Callback(configuration.callback.version) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                configuration.callback.onCreate(db)
                            }

                            override fun onOpen(db: SupportSQLiteDatabase) {
                                configuration.callback.onCreate(db)
                                EventBus.getDefault().post(DatabaseOpen())
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int
                            ) {
                                Log.d("onUpgrade", "Pre-migration")
                                EventBus.getDefault().post(PreMigration(oldVersion, newVersion))
                                configuration.callback.onUpgrade(db, oldVersion, newVersion)
                                Log.d("onUpgrade", "Post-migration")
                                EventBus.getDefault().post(PostMigration(oldVersion, newVersion))
                            }
                        })
                        .build()
                )
            }
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)

                    db.insert("Bike", OnConflictStrategy.ABORT, ContentValues().apply {
                        put("weight", getBikeMassOrNull(appContext))
                        put("wheelCircumference", getUserCircumferenceOrNull(appContext))
                        put("isDefault", 1)
                    })
                }
            }).build()

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

    @Provides
    @Singleton
    fun provideBikeDao(db: TripsDatabase): BikeDao {
        return db.bikeDao()
    }

    @Provides
    @Singleton
    fun provideExternalSensorDao(db: TripsDatabase): ExternalSensorDao {
        return db.externalSensorsDao()
    }

    @Provides
    @Singleton
    fun provideWeatherDao(db: TripsDatabase): WeatherDao {
        return db.weatherDao()
    }

    @Provides
    @Singleton
    fun provideCadenceSpeedMeasurementDao(db: TripsDatabase): CadenceSpeedMeasurementDao {
        return db.cadenceSpeedMeasurementDao()
    }

    @Provides
    @Singleton
    fun provideHeartRateMeasurementDao(db: TripsDatabase): HeartRateMeasurementDao {
        return db.heartRateMeasurementDao()
    }
}
