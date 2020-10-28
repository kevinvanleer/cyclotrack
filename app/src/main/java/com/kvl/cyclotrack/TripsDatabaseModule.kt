package com.kvl.cyclotrack

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
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

@Module
@InstallIn(ApplicationComponent::class)
object TripsDatabaseModule {

    @Provides
    @Singleton
    fun provideTripsDatabase(@ApplicationContext appContext: Context): TripsDatabase =
        Room.databaseBuilder(appContext, TripsDatabase::class.java, "trips-cyclotrack-kvl")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

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
}