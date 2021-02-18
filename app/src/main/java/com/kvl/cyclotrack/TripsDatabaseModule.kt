package com.kvl.cyclotrack

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
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
    fun provideSharedPreferences(@ApplicationContext appContext: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(appContext)
    }
}