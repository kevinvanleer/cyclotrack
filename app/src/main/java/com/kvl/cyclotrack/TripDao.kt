package com.kvl.cyclotrack

import androidx.lifecycle.LiveData
import androidx.room.*

data class TripInProgress(
    val id: Long,
    val inProgress: Boolean,
)

data class TripId(
    val id: Long,
)

data class TripStuff(
    val id: Long,
    val name: String,
    val notes: String?,
    val userWheelCircumference: Float?,
)

data class TripWheelCircumference(
    val id: Long,
    val userWheelCircumference: Float?,
    val autoWheelCircumference: Float?,
)

data class TripGoogleFitSync(
    val id: Long,
    val googleFitSyncStatus: GoogleFitSyncStatusEnum,
)

data class TripStravaSync(
    val id: Long,
    val stravaSyncStatus: GoogleFitSyncStatusEnum,
)

data class TripStats(
    val id: Long,
    val distance: Double?,
    val duration: Double?,
    val averageSpeed: Float?,
)

data class BikeTotals(
    val name: String,
    val distance: Double,
    val duration: Double,
    val count: Int,
)

data class DataBucket(
    val bucket: Int,
    val count: Int,
)

data class PeriodTotals(
    val period: String,
    val totalDistance: Double,
    val totalDuration: Double,
    val tripCount: Int,
)

data class TripAggregation(
    val totalDistance: Double,
    val totalDuration: Double,
    val tripCount: Int,
    val firstStart: Long,
    val lastStart: Long,
)

data class Biometrics(
    val id: Long,
    val userSex: UserSexEnum? = null,
    val userWeight: Float? = null,
    val userHeight: Float? = null,
    val userAge: Float? = null,
    val userVo2max: Float? = null,
    val userRestingHeartRate: Int? = null,
    val userMaxHeartRate: Int? = null,
)

@Dao
interface TripDao {
    @Insert
    fun save(trip: Trip): Long

    @Update
    fun update(vararg trips: Trip)

    @Update(entity = Trip::class)
    suspend fun updateInProgress(inProgress: TripInProgress)

    @Update(entity = Trip::class)
    suspend fun updateStats(stats: TripStats)

    @Update(entity = Trip::class)
    suspend fun updateCircumference(stats: TripWheelCircumference)

    @Update(entity = Trip::class)
    suspend fun updateStuff(stats: TripStuff)

    @Update(entity = Trip::class)
    suspend fun updateBiometrics(biometrics: Biometrics)

    @Update(entity = Trip::class)
    suspend fun updateStravaSyncStatus(stravaSyncStatus: TripStravaSync)

    @Update(entity = Trip::class)
    suspend fun updateGoogleFitSyncStatus(googleFitSyncStatus: TripGoogleFitSync)

    @Query("UPDATE trip SET bikeId=:bikeId where id = :id")
    suspend fun updateBikeId(id: Long, bikeId: Long)

    @Query("SELECT * FROM trip WHERE id = :tripId")
    fun subscribe(tripId: Long): LiveData<Trip>

    @Query("SELECT * FROM trip WHERE id = :tripId")
    suspend fun load(tripId: Long): Trip

    @Query("SELECT * from trip ORDER BY id DESC")
    fun subscribeAll(): LiveData<Array<Trip>>

    @Query("SELECT * from trip ORDER BY id DESC")
    fun loadAll(): Array<Trip>

    @Query("SELECT * FROM trip WHERE id > :tripId ORDER BY id DESC")
    suspend fun loadAfter(tripId: Long): Array<Trip>

    @Query("SELECT * FROM trip WHERE timestamp >= :start and timestamp < :end and distance > 1 AND duration > 60 ORDER BY timestamp ASC")
    fun subscribeDateRange(start: Long, end: Long): LiveData<Array<Trip>>

    @Query("SELECT * FROM trip WHERE stravaSyncStatus == 0 and inProgress = 0 ORDER BY id DESC")
    suspend fun loadStravaUnsyncedTrips(): Array<Trip>

    @Query("SELECT * FROM trip WHERE googleFitSyncStatus == 0 and inProgress = 0 ORDER BY id DESC")
    suspend fun loadGoogleFitUnsyncedTrips(): Array<Trip>

    @Query("SELECT * FROM trip WHERE googleFitSyncStatus == 4 ORDER BY id DESC")
    suspend fun loadGoogleFitDirtyTrips(): Array<Trip>

    @Query("SELECT * from trip WHERE distance > 1 AND duration > 60 ORDER BY id DESC")
    fun subscribeRealTrips(): LiveData<Array<Trip>>

    @Query("SELECT * from trip WHERE distance > 1 AND duration > 60 ORDER BY distance DESC LIMIT :limit")
    fun longestTrips(limit: Int): LiveData<Array<Trip>>

    @Query("SELECT * from trip WHERE timestamp = (SELECT max(timestamp) FROM trip)")
    suspend fun getNewestTrip(): Trip?

    @Query("SELECT * from trip WHERE timestamp = (SELECT max(timestamp) FROM trip)")
    fun observeNewestTrip(): LiveData<Trip>

    @Query("SELECT * from trip WHERE distance < 1 OR duration < 60")
    suspend fun getCleanupTrips(): Array<Trip>

    @Query("SELECT id,userSex,userWeight,userHeight,userAge,userVo2max,userRestingHeartRate,userMaxHeartRate from trip where timestamp = (SELECT min(timestamp) from trip where userWeight is not null and id >= :tripId)")
    suspend fun getDefaultBiometrics(tripId: Long): Biometrics?

    @Query("select sum(distance) as totalDistance, sum(duration) as totalDuration, count(*) as tripCount, min(timestamp) as firstStart, max(timestamp) as lastStart from trip where timestamp >= :start and timestamp < :end and distance > 1 AND duration > 60")
    fun subscribeTotals(start: Long, end: Long): LiveData<TripAggregation>

    @Query("select sum(distance) as totalDistance, sum(duration) as totalDuration, count(*) as tripCount, min(timestamp) as firstStart, max(timestamp) as lastStart from trip where distance > 1 AND duration > 60")
    fun subscribeTotals(): LiveData<TripAggregation>

    @Query("select strftime('%Y-%m-%d',  datetime(round(timestamp/1000),'unixepoch','localtime','start of day','weekday 6', '-6 day') ) as period, sum(distance) as totalDistance, sum(duration) as totalDuration, count(*) as tripCount from trip WHERE distance > 1 AND duration > 60 group by period order by totalDistance desc limit :limit")
    fun subscribeWeeklyTotals(limit: Int): LiveData<Array<PeriodTotals>>

    @Query("select strftime('%Y-%m',  datetime(round(timestamp/1000),'unixepoch','localtime') ) as period, sum(distance) as totalDistance, sum(duration) as totalDuration, count(*) as tripCount from trip  WHERE distance > 1 AND duration > 60 group by period order by totalDistance desc limit :limit")
    fun subscribeMonthlyTotals(limit: Int): LiveData<Array<PeriodTotals>>

    @Delete(entity = Trip::class)
    suspend fun removeTrip(id: TripId)

    @Delete(entity = Trip::class)
    suspend fun removeTrips(ids: Array<Trip>)

    @Query("DELETE from trip")
    fun wipe()

    @Query("select * from trip WHERE bikeId = :bikeId")
    fun getTripsForBike(bikeId: Long): Array<Trip>

    //increment buckets by 5 (5,10,15 mi)
    //select round(round(distance * 0.000621371 - 0.5) / 5 - 0.5) * 5 as bucket, count(*) as count from trip where bucket > 0 and timestamp > 1654949524000 group by bucket order by count desc, bucket desc limit 10
    @Query("select round(round(distance * :bucketFactor - 0.5) / :bucketSize - 0.5) * :bucketSize as bucket, count(*) as count from trip where bucket > 0 and timestamp > :timestamp group by bucket order by count desc, bucket desc limit :limit")
    fun getMostPopularDistances(
        bucketFactor: Double,
        timestamp: Long,
        bucketSize: Int,
        limit: Int,
    ): LiveData<Array<DataBucket>>

    @Query("select * from trip where round(round(distance * :bucketFactor - 0.5) / :bucketSize - 0.5) * :bucketSize = :distance order by averageSpeed desc limit :limit")
    fun getTripsOfDistance(
        distance: Int,
        bucketFactor: Double,
        bucketSize: Int,
        limit: Int
    ): LiveData<Array<Trip>>

    @Query("select total(distance) as distance, total(duration) as duration, count(*) as count, ifnull(bike.name, 'Bike ' || bike.id) name from trip LEFT OUTER JOIN bike on bike.id = bikeId group by bikeId order by distance desc")
    fun subscribeBikeTotals(): LiveData<Array<BikeTotals>>
}
