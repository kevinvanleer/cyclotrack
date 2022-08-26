package com.kvl.cyclotrack.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.kvl.cyclotrack.FeatureFlags
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private const val logTag = "GOOGLE_FIT_UTILITIES"

val fitnessOptions: FitnessOptions = FitnessOptions.builder().apply {
    addDataType(DataType.TYPE_WEIGHT, FitnessOptions.ACCESS_READ)
    addDataType(DataType.TYPE_HEIGHT, FitnessOptions.ACCESS_READ)
    addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)

    addDataType(DataType.TYPE_LOCATION_SAMPLE, FitnessOptions.ACCESS_WRITE)
    addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_WRITE)
    addDataType(DataType.TYPE_CYCLING_PEDALING_CADENCE, FitnessOptions.ACCESS_WRITE)
    addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_WRITE)
    addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_WRITE)

    if (FeatureFlags.devBuild) {
        addDataType(DataType.TYPE_CYCLING_PEDALING_CADENCE, FitnessOptions.ACCESS_READ)
        addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
        addDataType(DataType.TYPE_LOCATION_SAMPLE, FitnessOptions.ACCESS_READ)
        addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_READ)

        addDataType(DataType.AGGREGATE_HEIGHT_SUMMARY, FitnessOptions.ACCESS_READ)
        addDataType(DataType.AGGREGATE_WEIGHT_SUMMARY, FitnessOptions.ACCESS_READ)
        addDataType(DataType.AGGREGATE_HEART_RATE_SUMMARY, FitnessOptions.ACCESS_READ)
        addDataType(DataType.AGGREGATE_ACTIVITY_SUMMARY, FitnessOptions.ACCESS_READ)
    }
}.build()

fun getGoogleAccount(context: Context): GoogleSignInAccount? =
    GoogleSignIn.getAccountForExtension(context, fitnessOptions)

private fun printDataPoint(dataPoint: DataPoint) {
    val startString =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(
            Date(
                dataPoint.getStartTime(
                    TimeUnit.MILLISECONDS
                )
            )
        )
    val endString =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(
            Date(
                dataPoint.getEndTime(
                    TimeUnit.MILLISECONDS
                )
            )
        )
    when (dataPoint.dataType) {
        DataType.TYPE_HEIGHT -> {
            Log.v(
                logTag,
                "${startString}><${endString} HEIGHT: ${
                    dataPoint.getValue(Field.FIELD_HEIGHT)
                }"
            )
        }
        DataType.TYPE_WEIGHT -> {
            Log.v(
                logTag,
                "${startString}><${endString} WEIGHT: ${
                    dataPoint.getValue(Field.FIELD_WEIGHT)
                }"
            )
        }
        DataType.TYPE_SPEED -> {
            Log.v(
                logTag,
                "${startString}><${endString} M/S: ${
                    dataPoint.getValue(Field.FIELD_SPEED)
                }"
            )
        }
        DataType.TYPE_CYCLING_WHEEL_REVOLUTION -> {
            Log.v(
                logTag,
                "${startString}><${endString} REVS: ${
                    dataPoint.getValue(Field.FIELD_REVOLUTIONS)
                }"
            )
        }
        DataType.TYPE_CYCLING_WHEEL_RPM -> {
            Log.v(
                logTag,
                "${startString}><${endString} RPM: ${
                    dataPoint.getValue(Field.FIELD_RPM)
                }"
            )
        }
        DataType.TYPE_CYCLING_PEDALING_CUMULATIVE -> {
            Log.v(
                logTag,
                "${startString}><${endString} REVS: ${
                    dataPoint.getValue(Field.FIELD_REVOLUTIONS)
                }"
            )
        }
        DataType.TYPE_CYCLING_PEDALING_CADENCE -> {
            Log.v(
                logTag,
                "${startString}><${endString} RPM: ${
                    dataPoint.getValue(Field.FIELD_RPM)
                }"
            )
        }
        DataType.TYPE_LOCATION_SAMPLE -> {
            Log.v(
                logTag,
                "${startString}><${endString} LAT: ${
                    dataPoint.getValue(Field.FIELD_LATITUDE)
                }"
            )
            Log.v(
                logTag,
                "${startString}><${endString} LNG: ${
                    dataPoint.getValue(Field.FIELD_LONGITUDE)
                }"
            )
            Log.v(
                logTag,
                "${startString}><${endString} ALT: ${
                    dataPoint.getValue(Field.FIELD_ALTITUDE)
                }"
            )
            Log.v(
                logTag,
                "${startString}><${endString} ACC: ${
                    dataPoint.getValue(Field.FIELD_ACCURACY)
                }"
            )
        }
        DataType.TYPE_DISTANCE_DELTA -> {
            Log.v(
                logTag,
                "${startString}><${endString} M: ${
                    dataPoint.getValue(Field.FIELD_DISTANCE)
                }"
            )
        }
        DataType.TYPE_HEART_RATE_BPM -> {
            Log.v(
                logTag,
                "${startString}><${endString} BPM: ${
                    dataPoint.getValue(Field.FIELD_BPM)
                }"
            )
        }
        DataType.AGGREGATE_HEIGHT_SUMMARY -> {
            Log.v(
                logTag,
                "${startString}><${endString} AVG HEIGHT: ${
                    dataPoint.getValue(Field.FIELD_AVERAGE)
                }"
            )
            Log.v(
                logTag,
                "${startString}><${endString} MIN HEIGHT: ${
                    dataPoint.getValue(Field.FIELD_MIN)
                }"
            )
            Log.v(
                logTag,
                "${startString}><${endString} MAX HEIGHT: ${
                    dataPoint.getValue(Field.FIELD_MAX)
                }"
            )
        }
        DataType.AGGREGATE_WEIGHT_SUMMARY -> {
            Log.v(
                logTag,
                "${startString}><${endString} AVG KG: ${
                    dataPoint.getValue(Field.FIELD_AVERAGE)
                }"
            )
            Log.v(
                logTag,
                "${startString}><${endString} MIN KG: ${
                    dataPoint.getValue(Field.FIELD_MIN)
                }"
            )
            Log.v(
                logTag,
                "${startString}><${endString} MAX KG: ${
                    dataPoint.getValue(Field.FIELD_MAX)
                }"
            )
        }
        DataType.AGGREGATE_HEART_RATE_SUMMARY -> {
            Log.v(
                logTag,
                "${startString}><${endString} AVG BPM: ${
                    dataPoint.getValue(Field.FIELD_AVERAGE)
                }"
            )
            Log.v(
                logTag,
                "${startString}><${endString} MIN BPM: ${
                    dataPoint.getValue(Field.FIELD_MIN)
                }"
            )
            Log.v(
                logTag,
                "${startString}><${endString} MAX BPM: ${
                    dataPoint.getValue(Field.FIELD_MAX)
                }"
            )
        }
    }
}

fun getActivities(activity: Activity, start: Long, end: Long) {
    val readRequest = DataReadRequest.Builder()
        .read(DataType.AGGREGATE_ACTIVITY_SUMMARY)
        .read(DataType.TYPE_WEIGHT)
        .read(DataType.TYPE_LOCATION_SAMPLE)
        .read(DataType.TYPE_CYCLING_PEDALING_CADENCE)
        .read(DataType.TYPE_HEART_RATE_BPM)
        .read(DataType.TYPE_SPEED)
        .setTimeRange(start, end, TimeUnit.MILLISECONDS)
        .bucketByActivitySegment(30, TimeUnit.MINUTES)
        .build()
    getGoogleAccount(activity)?.let {
        Fitness.getHistoryClient(activity, it)
            .readData(readRequest)
            .addOnSuccessListener { response ->
                // Use response data here
                Log.d(logTag, "OnSuccess()")
                Log.d(
                    logTag,
                    "DataSet: ${response.getDataSet(DataType.AGGREGATE_ACTIVITY_SUMMARY)}"
                )
                response.buckets.forEachIndexed { idx, it ->
                    if (it.activity == "biking") {
                        Log.d(logTag, "index: ${idx}")
                        Log.d(logTag, "activity: ${it.activity}")
                        Log.d(logTag, "startTime: ${it.getStartTime(TimeUnit.SECONDS)}")
                        Log.d(logTag, "endTime: ${it.getEndTime(TimeUnit.SECONDS)}")
                        it.dataSets.forEach { dataset ->
                            /*dataset.dataPoints.forEach { dataPoint ->
                                Log.d(logTag, "${dataPoint}")
                            }*/
                            Log.d(logTag, "dataset: ${dataset}")
                        }
                    }
                }
            }
            .addOnFailureListener { e -> Log.d(logTag, "getActivities::OnFailure()", e) }
    }
}

fun getDatasets(activity: Activity, start: Long, end: Long) {
    Log.d(logTag, "GoogleFitUtilities::getDatasets()")
    val readRequest = DataReadRequest.Builder()
        .read(DataType.TYPE_LOCATION_SAMPLE)
        .read(DataType.TYPE_CYCLING_PEDALING_CADENCE)
        .read(DataType.TYPE_HEART_RATE_BPM)
        .read(DataType.TYPE_SPEED)
        .read(DataType.TYPE_DISTANCE_DELTA)
        .setTimeRange(start, end, TimeUnit.MILLISECONDS)
        .setLimit(100)
        .build()
    getGoogleAccount(activity)?.let {
        Fitness.getHistoryClient(activity, it)
            .readData(readRequest)
            .addOnSuccessListener { response ->
                // Use response data here
                Log.d(logTag, "getDatasets::OnSuccess()")
                response.dataSets.forEach { dataset ->
                    Log.d(logTag, "data type: ${dataset.dataType.name}")
                    Log.d(logTag, "data points count: ${dataset.dataPoints.size}")
                    dataset.dataPoints.forEach { dataPoint ->
                        printDataPoint(dataPoint)
                    }
                }
            }
            .addOnFailureListener { e -> Log.d(logTag, "getActivities::OnFailure()", e) }
    }
}

fun getLatest(
    activity: Activity,
    type: DataType,
    timestamp: Long = SystemUtils.currentTimeMillis()
) {
    getGoogleAccount(activity)?.let {
        Fitness.getHistoryClient(activity, it)
            .readData(
                DataReadRequest.Builder().read(type).setLimit(1)
                    .setTimeRange(1, timestamp, TimeUnit.MILLISECONDS).build()
            )
            .addOnSuccessListener { response ->
                // Use response data here
                Log.d(logTag, "getLatest")
                response.dataSets.forEach { dataset ->
                    dataset.dataPoints.forEach { dataPoint ->
                        printDataPoint(dataPoint)
                    }
                }
            }
            .addOnFailureListener { e -> Log.d(logTag, "getLatest::OnFailure()", e) }
    }
}

fun getLatestHeartRate(activity: Activity) {
    getGoogleAccount(activity)?.let {
        val end = SystemUtils.currentTimeMillis()
        val start = end - 1000L * 60 * 60 * 24 * 30
        Fitness.getHistoryClient(activity, it)
            .readData(
                DataReadRequest.Builder().aggregate(DataType.TYPE_HEART_RATE_BPM)
                    .bucketByTime(30, TimeUnit.DAYS).setLimit(1)
                    .setTimeRange(start, end, TimeUnit.MILLISECONDS).build()
            )
            .addOnSuccessListener { response ->
                Log.d(logTag, "getLatestHeartRate")
                response.buckets.forEach { bucket ->
                    bucket.dataSets.forEach { dataset ->
                        dataset.dataPoints.forEach { dataPoint ->
                            printDataPoint(dataPoint)
                        }
                    }
                }
            }
            .addOnFailureListener { e -> Log.d(logTag, "getLatestHeartRate::OnFailure()", e) }
    }
}

fun getSessionId(tripId: Long) = "com.kvl.cyclotrack.trip-${tripId}"

fun accessGoogleFit(activity: Activity) {
    Log.d(logTag, "accessGoogleFit()")
    //val end = System.currentTimeMillis()
    //val start = end - 1000L * 60 * 60 * 24 * 30

    val startCal = GregorianCalendar.getInstance()
    val endCal = GregorianCalendar.getInstance()
    //startCal.set(2020, 7, 1)
    //endCal.set(2020, 9, 30)
    startCal.set(2021, 4, 3)
    endCal.set(2021, 4, 4)

    //getActivities(activity, start, end)
    //getAggBiometrics(activity, start, end)
    //getBiometricsHistory(activity, start, end)
    //getSessions(activity, startCal.timeInMillis , endCal.timeInMillis )
    getDatasets(activity, startCal.timeInMillis, endCal.timeInMillis)
    //getSession(activity, start, end)

    /*
    var inc = startCal
    while (inc < endCal) {
        val rangeStart = inc
        val rangeEnd = inc.clone() as GregorianCalendar
        rangeEnd.add(Calendar.DAY_OF_MONTH, 7)
        getBikingSessions(activity, rangeStart.timeInMillis , rangeEnd.timeInMillis )
        inc = rangeEnd
    }
    */

    //getBikingSessions(activity, startCal.timeInMillis , endCal.timeInMillis )
    getLatest(activity, DataType.TYPE_WEIGHT)
    getLatest(activity, DataType.TYPE_HEIGHT)
    getLatestHeartRate(activity)
}

fun hasFitnessPermissions(context: Context): Boolean =
    GoogleSignIn.hasPermissions(getGoogleAccount(context), fitnessOptions)

fun configureGoogleFit(activity: Activity) {
    with(activity) {
        if (!hasFitnessPermissions(this)) {
            Log.i(logTag, "Logging in with Google Fit")
            GoogleSignIn.requestPermissions(
                this,
                1,
                getGoogleAccount(this),
                fitnessOptions
            )
        } else {
            Log.i(logTag, "Already logged in to Google Fit")
        }
    }
}
