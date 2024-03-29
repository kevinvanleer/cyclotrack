package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessActivities
import com.google.android.gms.fitness.data.*
import com.google.android.gms.fitness.request.*
import com.google.android.gms.fitness.result.SessionReadResponse
import com.google.android.gms.tasks.Task
import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.HeartRateMeasurement
import com.kvl.cyclotrack.util.SystemUtils
import com.kvl.cyclotrack.util.fitnessOptions
import com.kvl.cyclotrack.util.getGoogleAccount
import com.kvl.cyclotrack.util.getSessionId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt

class GoogleFitApiService @Inject constructor(@ApplicationContext private val context: Context) {
    private val logTag = "GoogleFitApiService"

    suspend fun getLatestHeight(
        timestamp: Long = SystemUtils.currentTimeMillis(),
    ): Float? {
        var height: Float? = null
        getGoogleAccount(context)?.let { Fitness.getHistoryClient(context, it) }
            ?.readData(
                DataReadRequest.Builder().read(DataType.TYPE_HEIGHT).setLimit(1)
                    .setTimeRange(1, timestamp, TimeUnit.MILLISECONDS).build()
            )?.await()
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
        timestamp: Long = SystemUtils.currentTimeMillis(),
    ): Float? {
        var weight: Float? = null
        getGoogleAccount(context)?.let { Fitness.getHistoryClient(context, it) }
            ?.readData(
                DataReadRequest.Builder().read(DataType.TYPE_WEIGHT).setLimit(1)
                    .setTimeRange(1, timestamp, TimeUnit.MILLISECONDS).build()
            )?.await()
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

    suspend fun getLatestRestingHeartRate(timestamp: Long = SystemUtils.currentTimeMillis()): Int? {
        val end = timestamp / 1000
        val start = end - 60 * 60 * 24 * 30
        var hr: Int? = null
        getGoogleAccount(context)?.let { Fitness.getHistoryClient(context, it) }
            ?.readData(
                DataReadRequest.Builder()
                    .aggregate(
                        DataSource.Builder()
                            .setType(DataSource.TYPE_DERIVED)
                            .setDataType(DataType.TYPE_HEART_RATE_BPM)
                            .setAppPackageName("com.google.android.gms")
                            .setStreamName("resting_heart_rate<-merge_heart_rate_bpm")
                            .build()
                    )
                    .bucketByTime(30, TimeUnit.DAYS).setLimit(1)
                    .setTimeRange(start, end, TimeUnit.SECONDS).build()
            )?.await()
            ?.buckets?.forEach { bucket ->
                bucket.dataSets.forEach { dataset ->
                    dataset.dataPoints.forEach { dataPoint ->
                        hr = dataPoint.getValue(Field.FIELD_MIN).asFloat().roundToInt()
                    }
                }
            }

        return hr
    }

    private fun createLocationDataset(measurements: Array<Measurements>): DataSet {
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

    private fun createHeartRateDataset(measurements: Array<HeartRateMeasurement>): DataSet {
        val dataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_HEART_RATE_BPM)
            .setType(DataSource.TYPE_RAW)
            .build()
        val heartRates = DataSet.builder(dataSource)
        measurements.forEach {
            val dataPoint = DataPoint.builder(dataSource)
            it.heartRate.let { heartRate ->
                try {
                    dataPoint.setField(Field.FIELD_BPM, heartRate.toFloat())
                    dataPoint.setTimestamp(it.timestamp, TimeUnit.MILLISECONDS)
                    heartRates.add(dataPoint.build())
                } catch (e: IllegalArgumentException) {
                    Log.w(logTag, "Heart rate value out of range: $heartRate")
                }
            }
        }

        return heartRates.build()
    }

    private fun createCadenceDataset(measurements: Array<CadenceSpeedMeasurement>): DataSet {
        val dataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_CYCLING_PEDALING_CADENCE)
            .setType(DataSource.TYPE_DERIVED)
            .build()
        val cadenceData = DataSet.builder(dataSource)
        var lastMeasurements: CadenceSpeedMeasurement? = null
        measurements.forEach { current ->
            current.let { curr ->
                lastMeasurements?.takeIf { it.rpm != 0f }
                    ?.let { last ->
                        val dataPoint = DataPoint.builder(dataSource)
                        getRpm(
                            rev = curr.revolutions,
                            revLast = last.revolutions,
                            time = curr.lastEvent,
                            timeLast = last.lastEvent
                        ).takeIf { it.isFinite() }
                            ?.let { cadence ->
                                try {
                                    dataPoint.setField(Field.FIELD_RPM, cadence)
                                    dataPoint.setTimestamp(curr.timestamp, TimeUnit.MILLISECONDS)
                                    cadenceData.add(dataPoint.build())
                                } catch (e: IllegalArgumentException) {
                                    Log.w(logTag, "Cadence value out of range: $cadence")
                                }
                            }
                    }
                lastMeasurements = curr
            }
        }

        return cadenceData.build()
    }

    private fun createCrankRevolutionsDataset(measurements: Array<CadenceSpeedMeasurement>): DataSet {
        val dataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_CYCLING_PEDALING_CUMULATIVE)
            .setType(DataSource.TYPE_RAW)
            .build()
        val crankRevData = DataSet.builder(dataSource)
        var lastMeasurements: CadenceSpeedMeasurement? = null
        measurements.forEach {
            val dataPoint = DataPoint.builder(dataSource)
            it.revolutions.let { revs ->
                lastMeasurements?.let { last ->
                    dataPoint.setField(Field.FIELD_REVOLUTIONS, revs)
                    dataPoint.setTimeInterval(last.timestamp, it.timestamp, TimeUnit.MILLISECONDS)
                    crankRevData.add(dataPoint.build())
                }
            }
            lastMeasurements = it
        }

        return crankRevData.build()
    }

    private fun createWheelRevolutionsDataset(measurements: Array<CadenceSpeedMeasurement>): DataSet {
        val dataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_CYCLING_WHEEL_REVOLUTION)
            .setType(DataSource.TYPE_RAW)
            .build()
        val crankRevData = DataSet.builder(dataSource)
        var lastMeasurements: CadenceSpeedMeasurement? = null
        measurements.forEach {
            val dataPoint = DataPoint.builder(dataSource)
            it.revolutions.let { revs ->
                lastMeasurements?.let { last ->
                    dataPoint.setField(Field.FIELD_REVOLUTIONS, revs)
                    dataPoint.setTimeInterval(last.timestamp, it.timestamp, TimeUnit.MILLISECONDS)
                    crankRevData.add(dataPoint.build())
                }
            }
            lastMeasurements = it
        }

        return crankRevData.build()
    }

    private fun createDistanceDeltaDataset(measurements: Array<Measurements>): DataSet {
        val dataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_DISTANCE_DELTA)
            .setType(DataSource.TYPE_DERIVED)
            .build()
        val datasetBuilder = DataSet.builder(dataSource)
        var lastMeasurements: Measurements? = null
        measurements.forEach {
            val dataPoint = DataPoint.builder(dataSource)
            lastMeasurements?.let { last ->
                dataPoint.setField(Field.FIELD_DISTANCE, getDistance(it, last))
                dataPoint.setTimeInterval(last.time, it.time, TimeUnit.MILLISECONDS)
                datasetBuilder.add(dataPoint.build())
            }
            lastMeasurements = it
        }

        return datasetBuilder.build()
    }

    private fun createSpeedDataset(
        measurements: Array<CadenceSpeedMeasurement>,
        wheelCircumference: Float,
    ): DataSet {
        Log.d(logTag, "wheelCircumference=${wheelCircumference}")
        val dataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_SPEED)
            .setType(DataSource.TYPE_DERIVED)
            .build()
        val speedData = DataSet.builder(dataSource)
        var lastMeasurements: CadenceSpeedMeasurement? = null
        measurements.forEach { current ->
            current.let { curr ->
                lastMeasurements?.takeIf { it.rpm != 0f }
                    ?.let { last ->
                        getRpm(
                            rev = curr.revolutions,
                            revLast = last.revolutions,
                            curr.lastEvent,
                            last.lastEvent
                        ).takeIf { it.isFinite() }?.let { rpm ->
                            try {
                                val dataPoint = DataPoint.builder(dataSource)
                                    .setField(Field.FIELD_SPEED, rpm / 60 * wheelCircumference)
                                    .setTimestamp(curr.timestamp, TimeUnit.MILLISECONDS)
                                speedData.add(dataPoint.build())
                            } catch (e: IllegalArgumentException) {
                                Log.w(
                                    logTag,
                                    "Speed value out of range: ${rpm / 60 * wheelCircumference}"
                                )
                            }
                        }
                    }
                lastMeasurements = curr
            }
        }

        return speedData.build()
    }

    private fun updateDataset(dataset: DataSet, startTime: Long, endTime: Long) {
        Log.d(logTag, "Update ${dataset.dataPoints.size} data points in ${dataset.dataType.name}")
        Log.d(
            logTag,
            "with time interval ${startTime}-${endTime}"
        )

        if (dataset.dataPoints.isNotEmpty()) {
            val updateRequest = DataUpdateRequest.Builder()
                .setDataSet(dataset)
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS).build()

            getGoogleAccount(context)?.let { Fitness.getHistoryClient(context, it) }
                ?.updateData(updateRequest)
                ?.addOnSuccessListener {
                    Log.d(
                        logTag,
                        "Updated ${dataset.dataPoints.size} data points in ${dataset.dataType.name}"
                    )
                }
                ?.addOnFailureListener { e ->
                    Log.d(logTag, "Failed to insert data points in ${dataset.dataType.name}: $e")
                    //e.startResolutionForResult()
                }
        }
    }

    private fun insertDataset(dataset: DataSet) {
        if (dataset.dataPoints.isNotEmpty()) {
            Log.d(
                logTag,
                "Insert ${dataset.dataPoints.size} data points in ${dataset.dataType.name}"
            )
            Log.d(
                logTag,
                "with time interval ${
                    dataset.dataPoints.first().getTimestamp(TimeUnit.MILLISECONDS)
                }-${dataset.dataPoints.last().getTimestamp(TimeUnit.MILLISECONDS)}"
            )

            getGoogleAccount(context)?.let { Fitness.getHistoryClient(context, it) }
                ?.insertData(dataset)
                ?.addOnSuccessListener {
                    Log.d(
                        logTag,
                        "Inserted ${dataset.dataPoints.size} data points in ${dataset.dataType.name}"
                    )
                }
                ?.addOnFailureListener { e ->
                    Log.d(logTag, "Failed to insert data points in ${dataset.dataType.name}: $e")
                    //e.startResolutionForResult()
                }
        } else {
            Log.w(logTag, "No data to insert")
        }
    }

    private fun insertHeartRateDataset(measurements: Array<HeartRateMeasurement>) {
        try {
            insertDataset(createHeartRateDataset(measurements))
        } catch (e: NoSuchElementException) {
            Log.w(logTag, "No heart rate data for this trip", e)
        }
    }

    private fun updateHeartRateDataset(measurements: Array<HeartRateMeasurement>) {
        updateDataset(
            createHeartRateDataset(measurements),
            measurements.first().timestamp,
            measurements.last().timestamp
        )
    }

    fun insertCrankRevolutionsDataset(measurements: Array<CadenceSpeedMeasurement>) {
        insertDataset(createCrankRevolutionsDataset(measurements))
    }

    fun updateCrankRevolutionsDataset(measurements: Array<CadenceSpeedMeasurement>) {
        updateDataset(
            createCrankRevolutionsDataset(measurements),
            measurements.first().timestamp,
            measurements.last().timestamp
        )
    }

    fun insertWheelRevolutionsDataset(measurements: Array<CadenceSpeedMeasurement>) {
        insertDataset(createWheelRevolutionsDataset(measurements))
    }

    fun updateWheelRevolutionsDataset(measurements: Array<CadenceSpeedMeasurement>) {
        updateDataset(
            createWheelRevolutionsDataset(measurements),
            measurements.first().timestamp,
            measurements.last().timestamp
        )
    }

    private fun insertLocationDataset(measurements: Array<Measurements>) {
        insertDataset(createLocationDataset(measurements))
    }

    fun updateLocationDataset(measurements: Array<Measurements>) {
        updateDataset(
            createLocationDataset(measurements),
            measurements.first().time,
            measurements.last().time
        )
    }

    private fun insertDistanceDeltaDataset(measurements: Array<Measurements>) {
        insertDataset(createDistanceDeltaDataset(measurements))
    }

    private fun updateDistanceDeltaDataset(measurements: Array<Measurements>) {
        updateDataset(
            createDistanceDeltaDataset(measurements),
            measurements.first().time,
            measurements.last().time
        )
    }

    private fun insertCadenceDataset(measurements: Array<CadenceSpeedMeasurement>) {
        insertDataset(createCadenceDataset(measurements))
    }

    private fun updateCadenceDataset(measurements: Array<CadenceSpeedMeasurement>) {
        updateDataset(
            createCadenceDataset(measurements),
            measurements.first().timestamp,
            measurements.last().timestamp
        )
    }

    private fun insertSpeedDataset(
        measurements: Array<CadenceSpeedMeasurement>,
        wheelCircumference: Float,
    ) {
        insertDataset(createSpeedDataset(measurements, wheelCircumference))
    }

    private fun updateSpeedDataset(
        measurements: Array<CadenceSpeedMeasurement>,
        wheelCircumference: Float,
    ) {
        updateDataset(
            createSpeedDataset(measurements, wheelCircumference),
            measurements.first().timestamp,
            measurements.last().timestamp
        )
    }

    fun insertDatasets(
        measurements: Array<Measurements>,
        heartRateMeasurements: Array<HeartRateMeasurement>,
        speedMeasurements: Array<CadenceSpeedMeasurement>,
        cadenceMeasurements: Array<CadenceSpeedMeasurement>,
        wheelCircumference: Float
    ) {
        measurements.toList().chunked(1000).forEach {
            Log.d(logTag, "Processing measurements chunk")
            insertHeartRateDataset(heartRateMeasurements)
            insertLocationDataset(it.toTypedArray())
            insertDistanceDeltaDataset(it.toTypedArray())
            insertCadenceDataset(cadenceMeasurements)
            insertSpeedDataset(speedMeasurements, wheelCircumference)
            insertCrankRevolutionsDataset(cadenceMeasurements)
            insertWheelRevolutionsDataset(speedMeasurements)
        }
    }

    fun updateDatasets(
        measurements: Array<Measurements>,
        heartRateMeasurements: Array<HeartRateMeasurement>,
        speedMeasurements: Array<CadenceSpeedMeasurement>,
        cadenceMeasurements: Array<CadenceSpeedMeasurement>,
        wheelCircumference: Float
    ) {
        measurements.toList().chunked(1000).forEach {
            Log.d(logTag, "Processing measurements chunk")
            updateHeartRateDataset(heartRateMeasurements)
            updateLocationDataset(it.toTypedArray())
            updateDistanceDeltaDataset(it.toTypedArray())
            updateCadenceDataset(cadenceMeasurements)
            updateSpeedDataset(speedMeasurements, wheelCircumference)
            updateCrankRevolutionsDataset(cadenceMeasurements)
            updateWheelRevolutionsDataset(speedMeasurements)
        }
    }

    private fun getActivitySegments(sessionId: String, timeStates: Array<TimeState>): DataSet {
        val contextSegmentDataSource = DataSource.Builder()
            .setAppPackageName(context)
            .setDataType(DataType.TYPE_ACTIVITY_SEGMENT)
            .setStreamName("$sessionId-context-segments")
            .setType(DataSource.TYPE_RAW)
            .build()

        val contextSegments = DataSet.builder(contextSegmentDataSource)

        getTripIntervals(timeStates).let { intervals ->
            intervals.forEach { interval ->
                DataPoint.builder(contextSegmentDataSource)
                    .setActivityField(Field.FIELD_ACTIVITY, FitnessActivities.BIKING)
                    .setTimeInterval(interval.first, interval.last, TimeUnit.MILLISECONDS)
                    .build().let {
                        contextSegments.add(it)
                    }
            }
        }
        return contextSegments.build()
    }


    private fun updateSession(
        trip: Trip,
        start: Long,
        end: Long,
        activeTime: Long,
    ) {
        val sessionBuilder = Session.Builder()
            .setIdentifier(getSessionId(trip.id!!))
            .setActivity(FitnessActivities.BIKING_ROAD)
            .setActiveTime(activeTime, TimeUnit.MILLISECONDS)
            .setStartTime(start, TimeUnit.MILLISECONDS)
            .setEndTime(end, TimeUnit.MILLISECONDS)

        trip.name?.let { sessionBuilder.setName(it) }
        trip.notes?.let { sessionBuilder.setDescription(it) }

        val insertRequest = SessionInsertRequest.Builder()
            .setSession(sessionBuilder.build())

        getGoogleAccount(context)?.let { Fitness.getSessionsClient(context, it) }
            ?.insertSession(insertRequest.build())
            ?.addOnSuccessListener { Log.d(logTag, "Ingested session for trip ${trip.id}") }
            ?.addOnFailureListener { Log.d(logTag, "Failed to insert session for trip ${trip.id}") }
    }

    fun updateSession(
        trip: Trip,
        start: Long,
        end: Long,
    ) {
        updateSession(trip, start, end, end - start)
    }

    fun updateSession(
        trip: Trip,
        timeStates: Array<TimeState>,
    ) {
        getStartTime(timeStates)?.let { start ->
            getEndTime(timeStates)?.let { end ->
                updateSession(
                    trip,
                    start,
                    end,
                    (accumulateActiveTime(timeStates) * 1000.0).toLong()
                )
            }
        }
    }

    fun insertSession(
        trip: Trip,
        timeStates: Array<TimeState>,
    ) {
        val sessionId = getSessionId(trip.id!!)
        val sessionBuilder = Session.Builder()
            .setIdentifier(sessionId)
            .setActivity(FitnessActivities.BIKING_ROAD)
            .setActiveTime(accumulateActiveTime(timeStates).toLong(), TimeUnit.SECONDS)

        trip.name?.let { sessionBuilder.setName(it) }
        trip.notes?.let { sessionBuilder.setDescription(it) }
        getStartTime(timeStates)?.let { sessionBuilder.setStartTime(it, TimeUnit.MILLISECONDS) }
        getEndTime(timeStates)?.let { sessionBuilder.setEndTime(it, TimeUnit.MILLISECONDS) }

        val insertRequest = SessionInsertRequest.Builder()
            .setSession(sessionBuilder.build())

        getActivitySegments(sessionId, timeStates).takeIf { it.dataPoints.isNotEmpty() }
            ?.let { insertRequest.addDataSet(it) }

        getGoogleAccount(context)?.let { Fitness.getSessionsClient(context, it) }
            ?.insertSession(insertRequest.build())
            ?.addOnSuccessListener { Log.d(logTag, "Ingested session for trip ${trip.id}") }
            ?.addOnFailureListener { Log.d(logTag, "Failed to insert session for trip ${trip.id}") }
    }

    fun insertSession(
        trip: Trip,
        start: Long,
        end: Long,
    ) {
        val sessionId = getSessionId(trip.id!!)
        val sessionBuilder = Session.Builder()
            .setIdentifier(sessionId)
            .setActivity(FitnessActivities.BIKING_ROAD)
            .setActiveTime(end - start, TimeUnit.MILLISECONDS)
            .setStartTime(start, TimeUnit.MILLISECONDS)
            .setEndTime(end, TimeUnit.MILLISECONDS)

        trip.name?.let { sessionBuilder.setName(it) }
        trip.notes?.let { sessionBuilder.setDescription(it) }

        val insertRequest = SessionInsertRequest.Builder()
            .setSession(sessionBuilder.build())

        getGoogleAccount(context)?.let { Fitness.getSessionsClient(context, it) }
            ?.insertSession(insertRequest.build())
            ?.addOnSuccessListener { Log.d(logTag, "Ingested session for trip ${trip.id}") }
            ?.addOnFailureListener { Log.d(logTag, "Failed to insert session for trip ${trip.id}") }
    }

    fun getSession(trip: Trip): Task<SessionReadResponse>? {
        val sessionId = getSessionId(trip.id!!)
        val start = trip.timestamp - (1000 * 60 * 60)
        val end = trip.timestamp + (trip.duration?.times(1000.0)!! + (1000 * 60 * 60)).toLong()

        val readRequest = SessionReadRequest.Builder()
            .setSessionId(sessionId)
            .setTimeInterval(start, end, TimeUnit.MILLISECONDS)
            .build()
        return getGoogleAccount(context)?.let {
            Fitness.getSessionsClient(context, it)
                .readSession(readRequest)
        }
    }

    fun getSession(sessionId: String): Task<SessionReadResponse>? {
        val readRequest = SessionReadRequest.Builder()
            .setSessionId(sessionId)
            .build()
        return getGoogleAccount(context)?.let {
            Fitness.getSessionsClient(context, it)
                .readSession(readRequest)
        }
    }

    fun getSession(start: Long, end: Long): Task<SessionReadResponse>? {
        val readRequest = SessionReadRequest.Builder()
            .setTimeInterval(start, end, TimeUnit.MILLISECONDS)
            .build()
        return getGoogleAccount(context)?.let {
            Fitness.getSessionsClient(context, it)
                .readSession(readRequest)
        }
    }

    fun deleteTrip(tripId: Long, startTime: Long, endTime: Long) {
        val request = DataDeleteRequest.Builder()
            .setTimeInterval(
                startTime,
                endTime,
                TimeUnit.MILLISECONDS
            )
            .deleteAllData()
            .deleteAllSessions().build()
        getGoogleAccount(context)?.let { Fitness.getHistoryClient(context, it) }
            ?.deleteData(request)
            ?.addOnSuccessListener { Log.d(logTag, "Removing trip ${tripId} from Google Fit") }
            ?.addOnFailureListener {
                Log.e(
                    logTag,
                    "Failed to remove trip ${tripId} from Google Fit", it
                )
            }
    }

    fun deleteTrip(trip: Trip, timeStates: Array<TimeState>) {
        timeStates.takeIf { it.isNotEmpty() }?.let {
            deleteTrip(trip.id!!, getStartTime(it)!!, getEndTime(it)!!)
        } ?: deleteTrip(
            trip.id!!,
            trip.timestamp,
            (trip.timestamp + (trip.duration?.times(1000) ?: 0L).toLong())
        )
    }

    fun deleteAllData() {
        val request = DataDeleteRequest.Builder()
            .setTimeInterval(/*1577836800000*/1262304000000,
                SystemUtils.currentTimeMillis(),
                TimeUnit.MILLISECONDS
            )
            .deleteAllData()
            .deleteAllSessions().build()
        getGoogleAccount(context)?.let { Fitness.getHistoryClient(context, it) }
            ?.deleteData(request)
            ?.addOnSuccessListener { Log.d(logTag, "Removing all data from Google Fit") }
            ?.addOnFailureListener {
                Log.e(
                    logTag,
                    "Failed to remove all data from Google Fit", it
                )
            }
    }

    fun hasPermission(): Boolean =
        GoogleSignIn.hasPermissions(getGoogleAccount(context), fitnessOptions)
}
