package com.kvl.cyclotrack

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessActivities
import com.google.android.gms.fitness.data.*
import com.google.android.gms.fitness.request.DataDeleteRequest
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.DataUpdateRequest
import com.google.android.gms.fitness.request.SessionInsertRequest
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class GoogleFitApiService constructor(private val context: Context) {
    private val logTag = "GoogleFitApiService"
    private val activity = context as Activity

    companion object {
        lateinit var instance: GoogleFitApiService private set
    }

    init {
        instance = this
    }

    suspend fun getLatestHeight(
        timestamp: Long = System.currentTimeMillis(),
    ): Float? {
        var height: Float? = null
        getGoogleAccount(activity)?.let { Fitness.getHistoryClient(activity, it) }
            ?.readData(DataReadRequest.Builder().read(DataType.TYPE_HEIGHT).setLimit(1)
                .setTimeRange(1, timestamp, TimeUnit.MILLISECONDS).build())?.await()
            ?.dataSets?.forEach { dataset ->
                dataset.dataPoints.forEach { dataPoint ->
                    when (dataPoint.dataType) {
                        DataType.TYPE_HEIGHT ->
                            height = dataPoint.getValue(Field.FIELD_HEIGHT).asFloat()
                    }
                }
            }
        return height
    }

    suspend fun getLatestWeight(
        timestamp: Long = System.currentTimeMillis(),
    ): Float? {
        var weight: Float? = null
        getGoogleAccount(activity)?.let { Fitness.getHistoryClient(activity, it) }
            ?.readData(DataReadRequest.Builder().read(DataType.TYPE_WEIGHT).setLimit(1)
                .setTimeRange(1, timestamp, TimeUnit.MILLISECONDS).build())?.await()
            ?.dataSets?.forEach { dataset ->
                dataset.dataPoints.forEach { dataPoint ->
                    when (dataPoint.dataType) {
                        DataType.TYPE_WEIGHT ->
                            weight = dataPoint.getValue(Field.FIELD_WEIGHT).asFloat()
                    }
                }
            }
        return weight
    }

    suspend fun getLatestRestingHeartRate(timestamp: Long = System.currentTimeMillis()): Int? {
        val end = timestamp / 1000
        val start = end - 60 * 60 * 24 * 30
        var hr: Int? = null
        getGoogleAccount(activity)?.let { Fitness.getHistoryClient(activity, it) }
            ?.readData(DataReadRequest.Builder().aggregate(DataType.TYPE_HEART_RATE_BPM)
                .bucketByTime(30, TimeUnit.DAYS).setLimit(1)
                .setTimeRange(start, end, TimeUnit.SECONDS).build())?.await()
            ?.buckets?.forEach { bucket ->
                bucket.dataSets.forEach { dataset ->
                    dataset.dataPoints.forEach { dataPoint ->
                        hr = dataPoint.getValue(Field.FIELD_MIN).asFloat().roundToInt()
                    }
                }
            }
        return hr
    }

    private fun createLocationDataset(measurements: Array<CriticalMeasurements>): DataSet {
        val dataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_LOCATION_SAMPLE)
            .setType(DataSource.TYPE_RAW)
            .build()
        val locations = DataSet.builder(dataSource)
        measurements.forEach {
            val dataPoint = DataPoint.builder(dataSource)
                .setField(Field.FIELD_LATITUDE, it.latitude.toFloat())
                .setField(Field.FIELD_LONGITUDE, it.longitude.toFloat())
                .setField(Field.FIELD_ALTITUDE, it.altitude.toFloat())
                .setField(Field.FIELD_ACCURACY, it.accuracy)
                .setTimestamp(it.time, TimeUnit.MILLISECONDS)
            locations.add(dataPoint.build())
        }

        return locations.build()
    }

    private fun createHeartRateDataset(measurements: Array<CriticalMeasurements>): DataSet {
        val dataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_HEART_RATE_BPM)
            .setType(DataSource.TYPE_RAW)
            .build()
        val heartRates = DataSet.builder(dataSource)
        measurements.forEach {
            val dataPoint = DataPoint.builder(dataSource)
            it.heartRate?.let { heartRate ->
                dataPoint.setField(Field.FIELD_BPM, heartRate.toFloat())
                dataPoint.setTimestamp(it.time, TimeUnit.MILLISECONDS)
                heartRates.add(dataPoint.build())
            }
        }

        return heartRates.build()
    }

    private fun createCadenceDataset(measurements: Array<CriticalMeasurements>): DataSet {
        val dataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_CYCLING_PEDALING_CADENCE)
            .setType(DataSource.TYPE_DERIVED)
            .build()
        val cadenceData = DataSet.builder(dataSource)
        var lastMeasurements: CriticalMeasurements? = null
        measurements.forEach { curr ->
            lastMeasurements?.let { last ->
                val dataPoint = DataPoint.builder(dataSource)
                getRpm(rev = curr.cadenceRevolutions ?: 0,
                    revLast = last.cadenceRevolutions ?: 0,
                    time = curr.cadenceLastEvent ?: 0,
                    timeLast = last.cadenceLastEvent ?: 0)?.takeIf { it.isFinite() }
                    ?.let { cadence ->
                        dataPoint.setField(Field.FIELD_RPM, cadence)
                        dataPoint.setTimestamp(curr.time, TimeUnit.MILLISECONDS)
                        cadenceData.add(dataPoint.build())
                    }
            }
            lastMeasurements = curr
        }

        return cadenceData.build()
    }

    private fun createCrankRevolutionsDataset(measurements: Array<CriticalMeasurements>): DataSet {
        val dataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_CYCLING_PEDALING_CUMULATIVE)
            .setType(DataSource.TYPE_RAW)
            .build()
        val crankRevData = DataSet.builder(dataSource)
        measurements.forEach {
            val dataPoint = DataPoint.builder(dataSource)
            it.cadenceRevolutions?.let { revs ->
                dataPoint.setField(Field.FIELD_REVOLUTIONS, revs)
                dataPoint.setTimestamp(it.time, TimeUnit.MILLISECONDS)
                crankRevData.add(dataPoint.build())
            }
        }

        return crankRevData.build()
    }

    private fun createSpeedDataset(
        measurements: Array<CriticalMeasurements>,
        wheelCircumference: Float,
    ): DataSet {
        val dataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_SPEED)
            .setType(DataSource.TYPE_DERIVED)
            .build()
        val speedData = DataSet.builder(dataSource)
        var lastMeasurements: CriticalMeasurements? = null
        measurements.forEach { curr ->
            lastMeasurements?.let { last ->
                getRpm(rev = curr.speedRevolutions ?: 0,
                    revLast = last.speedRevolutions ?: 0,
                    curr.speedLastEvent ?: 0,
                    last.speedLastEvent ?: 0)?.takeIf { it.isFinite() }?.let { speedRpm ->
                    val dataPoint = DataPoint.builder(dataSource)
                        .setField(Field.FIELD_SPEED, speedRpm / 60 * wheelCircumference)
                        .setTimestamp(curr.time, TimeUnit.MILLISECONDS)
                    speedData.add(dataPoint.build())
                }
            }
            lastMeasurements = curr
        }

        return speedData.build()
    }

    fun updateDataset(dataset: DataSet, startTime: Long, endTime: Long) {
        Log.d(logTag, "Update ${dataset.dataPoints.size} data points in ${dataset.dataType.name}")
        Log.d(logTag,
            "with time interval ${
                dataset.dataPoints.first().getTimestamp(TimeUnit.MILLISECONDS)
            }-${dataset.dataPoints.last().getTimestamp(TimeUnit.MILLISECONDS)}")

        val updateRequest = DataUpdateRequest.Builder()
            .setDataSet(dataset)
            .setTimeInterval(dataset.dataPoints.first().getTimestamp(TimeUnit.MILLISECONDS),
                dataset.dataPoints.last().getTimestamp(TimeUnit.MILLISECONDS),
                TimeUnit.MILLISECONDS).build()

        getGoogleAccount(activity)?.let { Fitness.getHistoryClient(activity, it) }
            ?.updateData(updateRequest)
            ?.addOnSuccessListener {
                Log.d(logTag,
                    "Updated ${dataset.dataPoints.size} data points in ${dataset.dataType.name}")
            }
            ?.addOnFailureListener { e ->
                Log.d(logTag, "Failed to insert data points in ${dataset.dataType.name}: $e")
                //e.startResolutionForResult()
            }

    }

    private fun insertDataset(dataset: DataSet) {
        Log.d(logTag, "Insert ${dataset.dataPoints.size} data points in ${dataset.dataType.name}")
        Log.d(logTag,
            "with time interval ${
                dataset.dataPoints.first().getTimestamp(TimeUnit.MILLISECONDS)
            }-${dataset.dataPoints.last().getTimestamp(TimeUnit.MILLISECONDS)}")

        getGoogleAccount(activity)?.let { Fitness.getHistoryClient(activity, it) }
            ?.insertData(dataset)
            ?.addOnSuccessListener {
                Log.d(logTag,
                    "Inserted ${dataset.dataPoints.size} data points in ${dataset.dataType.name}")
            }
            ?.addOnFailureListener { e ->
                Log.d(logTag, "Failed to insert data points in ${dataset.dataType.name}: $e")
                //e.startResolutionForResult()
            }
    }

    fun insertHeartRateDataset(measurements: Array<CriticalMeasurements>) {
        insertDataset(createHeartRateDataset(measurements))
    }

    fun updateHeartRateDataset(measurements: Array<CriticalMeasurements>) {
        updateDataset(createHeartRateDataset(measurements),
            measurements.first().time,
            measurements.last().time)
    }

    fun insertCrankRevolutionsDataset(measurements: Array<CriticalMeasurements>) {
        insertDataset(createCrankRevolutionsDataset(measurements))
    }

    fun updateCrankRevolutionsDataset(measurements: Array<CriticalMeasurements>) {
        updateDataset(createCrankRevolutionsDataset(measurements),
            measurements.first().time,
            measurements.last().time)
    }

    fun insertLocationDataset(measurements: Array<CriticalMeasurements>) {
        insertDataset(createLocationDataset(measurements))
    }

    fun updateLocationDataset(measurements: Array<CriticalMeasurements>) {
        updateDataset(createLocationDataset(measurements),
            measurements.first().time,
            measurements.last().time)
    }

    fun insertCadenceDataset(measurements: Array<CriticalMeasurements>) {
        insertDataset(createCadenceDataset(measurements))
    }

    fun updateCadenceDataset(measurements: Array<CriticalMeasurements>) {
        updateDataset(createCadenceDataset(measurements),
            measurements.first().time,
            measurements.last().time)
    }

    fun insertSpeedDataset(measurements: Array<CriticalMeasurements>, wheelCircumference: Float) {
        insertDataset(createSpeedDataset(measurements, wheelCircumference))
    }

    fun updateSpeedDataset(measurements: Array<CriticalMeasurements>, wheelCircumference: Float) {
        updateDataset(createSpeedDataset(measurements, wheelCircumference),
            measurements.first().time,
            measurements.last().time)
    }

    fun insertDatasets(measurements: Array<CriticalMeasurements>, wheelCircumference: Float) {
        measurements.toList().chunked(1000).forEach {
            Log.d(logTag, "Processing measurements chunk")
            insertHeartRateDataset(it.toTypedArray())
            insertCrankRevolutionsDataset(it.toTypedArray())
            insertLocationDataset(it.toTypedArray())
            insertCadenceDataset(it.toTypedArray())
            insertSpeedDataset(it.toTypedArray(), wheelCircumference)
        }
    }

    fun updateDatasets(measurements: Array<CriticalMeasurements>, wheelCircumference: Float) {
        measurements.toList().chunked(1000).forEach {
            Log.d(logTag, "Processing measurements chunk")
            updateHeartRateDataset(it.toTypedArray())
            updateCrankRevolutionsDataset(it.toTypedArray())
            updateLocationDataset(it.toTypedArray())
            updateCadenceDataset(it.toTypedArray())
            updateSpeedDataset(it.toTypedArray(), wheelCircumference)
        }
    }

    private fun getActivitySegments(sessionId: String, timeStates: Array<TimeState>): DataSet {
        val activitySegmentDataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_ACTIVITY_SEGMENT)
            .setStreamName("$sessionId-activity-segments")
            .setType(DataSource.TYPE_RAW)
            .build()

        val activitySegments = DataSet.builder(activitySegmentDataSource)

        getTripIntervals(timeStates).let { intervals ->
            intervals.forEach { interval ->
                DataPoint.builder(activitySegmentDataSource)
                    .setActivityField(Field.FIELD_ACTIVITY, FitnessActivities.BIKING)
                    .setTimeInterval(interval.first, interval.last, TimeUnit.MILLISECONDS)
                    .build().let {
                        activitySegments.add(it)
                    }
            }
        }
        return activitySegments.build()
    }

    fun createSession(
        trip: Trip,
        timeStates: Array<TimeState>,
        measurements: Array<CriticalMeasurements>,
        wheelCircumference: Float,
    ) {
        val sessionId = "com.kvl.cyclotrack.trip-${trip.id}"
        val sessionBuilder = Session.Builder()
            .setIdentifier(sessionId)
            .setActivity(FitnessActivities.BIKING_ROAD)
            .setActiveTime(accumulateActiveTime(timeStates).toLong(), TimeUnit.SECONDS)

        trip.name?.let { sessionBuilder.setName(it) }
        trip.notes?.let { sessionBuilder.setDescription(it) }
        getStartTime(timeStates)?.let { sessionBuilder.setStartTime(it, TimeUnit.MILLISECONDS) }
        getEndTime(timeStates)?.let { sessionBuilder.setEndTime(it, TimeUnit.MILLISECONDS) }

        val session = sessionBuilder.build()
        val sessionMeasurements = measurements.filter {
            it.time <= session.getEndTime(TimeUnit.MILLISECONDS) && it.time >= session.getStartTime(
                TimeUnit.MILLISECONDS)
        }.toTypedArray()

        val insertRequest = SessionInsertRequest.Builder()
            //.addDataSet(createLocationDataset(sessionMeasurements))
            //.addDataSet(createHeartRateDataset(sessionMeasurements))
            //.addDataSet(createCrankRevolutionsDataset(sessionMeasurements))
            //.addDataSet(createCadenceDataset(sessionMeasurements))
            //.addDataSet(createSpeedDataset(sessionMeasurements, wheelCircumference))
            .addDataSet(getActivitySegments(sessionId, timeStates))
            .setSession(sessionBuilder.build())

        getGoogleAccount(activity)?.let { Fitness.getSessionsClient(activity, it) }
            ?.insertSession(insertRequest.build())
            ?.addOnSuccessListener { Log.d(logTag, "Ingested session for trip ${trip.id}") }
            ?.addOnFailureListener { Log.d(logTag, "Failed to insert session for trip ${trip.id}") }
    }

    fun deleteTrip(trip: Trip, timeStates: Array<TimeState>) {
        val request = DataDeleteRequest.Builder()
            .setTimeInterval(getStartTime(timeStates)!!,
                getEndTime(timeStates)!!,
                TimeUnit.MILLISECONDS)
            .deleteAllData()
            .deleteAllSessions().build()
        getGoogleAccount(activity)?.let { Fitness.getHistoryClient(activity, it) }
            ?.deleteData(request)
            ?.addOnSuccessListener { Log.d(logTag, "Removing trip ${trip.id} from Google Fit") }
            ?.addOnFailureListener {
                Log.d(logTag,
                    "Failed to remove trip ${trip.id} from Google Fit")
            }
    }

    fun hasPermission(): Boolean =
        GoogleSignIn.hasPermissions(getGoogleAccount(context), fitnessOptions)
}