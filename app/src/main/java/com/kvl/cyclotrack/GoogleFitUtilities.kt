package com.kvl.cyclotrack

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.SessionReadRequest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private const val logTag = "GOOGLE_FIT_UTILITIES"

val fitnessOptions = FitnessOptions.builder()
    .addDataType(DataType.AGGREGATE_HEIGHT_SUMMARY,
        FitnessOptions.ACCESS_READ)
    .addDataType(DataType.AGGREGATE_WEIGHT_SUMMARY,
        FitnessOptions.ACCESS_READ)
    .addDataType(DataType.AGGREGATE_HEART_RATE_SUMMARY,
        FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_CYCLING_WHEEL_REVOLUTION, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_CYCLING_WHEEL_RPM, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_CYCLING_PEDALING_CADENCE, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_WEIGHT, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_HEIGHT, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_ACTIVITY_SEGMENT,
        FitnessOptions.ACCESS_WRITE)
    .addDataType(DataType.AGGREGATE_ACTIVITY_SUMMARY,
        FitnessOptions.ACCESS_READ)
    .build()

fun getGoogleAccount(context: Context) = GoogleSignIn.getLastSignedInAccount(context)

private fun printDataPoint(dataPoint: DataPoint) {
    val startString =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(dataPoint.getStartTime(
            TimeUnit.MILLISECONDS)))
    val endString =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(dataPoint.getEndTime(
            TimeUnit.MILLISECONDS)))
    when (dataPoint.dataType) {
        DataType.TYPE_HEIGHT -> {
            Log.d(logTag,
                "${startString}><${endString} HEIGHT: ${
                    dataPoint.getValue(Field.FIELD_HEIGHT)
                }")
        }
        DataType.TYPE_WEIGHT -> {
            Log.d(logTag,
                "${startString}><${endString} WEIGHT: ${
                    dataPoint.getValue(Field.FIELD_WEIGHT)
                }")
        }
        DataType.TYPE_SPEED -> {
            Log.d(logTag,
                "${startString}><${endString} M/S: ${
                    dataPoint.getValue(Field.FIELD_SPEED)
                }")
        }
        DataType.TYPE_DISTANCE_DELTA -> {
            Log.d(logTag,
                "${startString}><${endString} M: ${
                    dataPoint.getValue(Field.FIELD_DISTANCE)
                }")
        }
        DataType.TYPE_HEART_RATE_BPM -> {
            Log.d(logTag,
                "${startString}><${endString} BPM: ${
                    dataPoint.getValue(Field.FIELD_BPM)
                }")
        }
        DataType.AGGREGATE_HEIGHT_SUMMARY -> {
            Log.d(logTag,
                "${startString}><${endString} AVG HEIGHT: ${
                    dataPoint.getValue(Field.FIELD_AVERAGE)
                }")
            Log.d(logTag,
                "${startString}><${endString} MIN HEIGHT: ${
                    dataPoint.getValue(Field.FIELD_MIN)
                }")
            Log.d(logTag,
                "${startString}><${endString} MAX HEIGHT: ${
                    dataPoint.getValue(Field.FIELD_MAX)
                }")
        }
        DataType.AGGREGATE_WEIGHT_SUMMARY -> {
            Log.d(logTag,
                "${startString}><${endString} AVG KG: ${
                    dataPoint.getValue(Field.FIELD_AVERAGE)
                }")
            Log.d(logTag,
                "${startString}><${endString} MIN KG: ${
                    dataPoint.getValue(Field.FIELD_MIN)
                }")
            Log.d(logTag,
                "${startString}><${endString} MAX KG: ${
                    dataPoint.getValue(Field.FIELD_MAX)
                }")
        }
        DataType.AGGREGATE_HEART_RATE_SUMMARY -> {
            Log.d(logTag,
                "${startString}><${endString} AVG BPM: ${
                    dataPoint.getValue(Field.FIELD_AVERAGE)
                }")
            Log.d(logTag,
                "${startString}><${endString} MIN BPM: ${
                    dataPoint.getValue(Field.FIELD_MIN)
                }")
            Log.d(logTag,
                "${startString}><${endString} MAX BPM: ${
                    dataPoint.getValue(Field.FIELD_MAX)
                }")
        }
    }
}

fun getActivities(activity: Activity, start: Long, end: Long) {
    val readRequest = DataReadRequest.Builder()
        //.aggregate(DataType.AGGREGATE_ACTIVITY_SUMMARY)
        .read(DataType.AGGREGATE_ACTIVITY_SUMMARY)
        .read(DataType.TYPE_WEIGHT)
        //.read(DataType.TYPE_LOCATION_SAMPLE) cannot be read
        .read(DataType.TYPE_HEART_RATE_BPM)
        .setTimeRange(start, end, TimeUnit.SECONDS)
        .bucketByActivitySegment(30, TimeUnit.MINUTES)
        .build()
    getGoogleAccount(activity)?.let {
        Fitness.getHistoryClient(activity, it)
            .readData(readRequest)
            .addOnSuccessListener { response ->
                // Use response data here
                Log.d(logTag, "OnSuccess()")
                Log.d(logTag,
                    "DataSet: ${response.getDataSet(DataType.AGGREGATE_ACTIVITY_SUMMARY)}")
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
            .addOnFailureListener { e -> Log.d(logTag, "OnFailure()", e) }
    }
}

fun getLatestWeight(
    activity: Activity,
    type: DataType,
    timestamp: Long = System.currentTimeMillis(),
) {
    getGoogleAccount(activity)?.let {
        Fitness.getHistoryClient(activity, it)
            .readData(DataReadRequest.Builder().read(type).setLimit(1)
                .setTimeRange(1, timestamp, TimeUnit.MILLISECONDS).build())
            .addOnSuccessListener { response ->
                // Use response data here
                Log.d(logTag, "getLatest")
                response.dataSets.forEach { dataset ->
                    dataset.dataPoints.forEach { dataPoint ->
                        when (dataPoint.dataType) {
                            DataType.TYPE_WEIGHT -> {
                                dataPoint.getValue(Field.FIELD_WEIGHT).asFloat()
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.d(logTag, "OnFailure()", e)
            }
    }
}

fun getLatest(activity: Activity, type: DataType, timestamp: Long = System.currentTimeMillis()) {
    getGoogleAccount(activity)?.let {
        Fitness.getHistoryClient(activity, it)
            .readData(DataReadRequest.Builder().read(type).setLimit(1)
                .setTimeRange(1, timestamp, TimeUnit.MILLISECONDS).build())
            .addOnSuccessListener { response ->
                // Use response data here
                Log.d(logTag, "getLatest")
                response.dataSets.forEach { dataset ->
                    dataset.dataPoints.forEach { dataPoint ->
                        printDataPoint(dataPoint)
                    }
                }
            }
            .addOnFailureListener { e -> Log.d(logTag, "OnFailure()", e) }
    }
}

fun getLatestHeartRate(activity: Activity) {
    getGoogleAccount(activity)?.let {
        val end = System.currentTimeMillis() / 1000
        val start = end - 60 * 60 * 24 * 30
        Fitness.getHistoryClient(activity, it)
            .readData(DataReadRequest.Builder().aggregate(DataType.TYPE_HEART_RATE_BPM)
                .bucketByTime(30, TimeUnit.DAYS).setLimit(1)
                .setTimeRange(start, end, TimeUnit.SECONDS).build())
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
            .addOnFailureListener { e -> Log.d(logTag, "OnFailure()", e) }
    }
}

fun getBiometricsHistory(activity: Activity, start: Long, end: Long) {
    val readRequest = DataReadRequest.Builder()
        .read(DataType.TYPE_HEIGHT)
        .read(DataType.TYPE_WEIGHT)
        .setTimeRange(start, end, TimeUnit.SECONDS)
        //.setTimeRange(1, end, TimeUnit.SECONDS)
        .bucketByTime(1, TimeUnit.DAYS)
        //.bucketByActivitySegment(30, TimeUnit.MINUTES)
        //.setLimit(1)
        .build()
    getGoogleAccount(activity)?.let {
        Fitness.getHistoryClient(activity, it)
            .readData(readRequest)
            .addOnSuccessListener { response ->
                // Use response data here
                Log.d(logTag, "getBiometricHistory")
                response.buckets.forEach { bucket ->
                    bucket.dataSets.forEach { dataset ->
                        dataset.dataPoints.forEach { dataPoint ->
                            printDataPoint(dataPoint)
                        }
                    }
                }
            }
            .addOnFailureListener { e -> Log.d(logTag, "OnFailure()", e) }
    }
}

fun getSession(activity: Activity, start: Long, end: Long) {
    val readRequest = SessionReadRequest.Builder()
        //.read(DataType.TYPE_HEIGHT)
        //.read(DataType.TYPE_WEIGHT)
        //.read(DataType.TYPE_LOCATION_SAMPLE)
        .read(DataType.TYPE_SPEED)
        .read(DataType.TYPE_DISTANCE_DELTA)
        //.read(DataType.TYPE_HEART_RATE_BPM)
        //.read(DataType.AGGREGATE_HEART_RATE_SUMMARY)
        //.read(DataType.AGGREGATE_HEIGHT_SUMMARY)
        //.read(DataType.AGGREGATE_WEIGHT_SUMMARY)
        .readSessionsFromAllApps()
        .excludePackage("com.kvl.cyclotrack.dev")
        .excludePackage("com.kvl.cyclotrack")
        .setTimeInterval(start, end, TimeUnit.SECONDS)
        .build()
    getGoogleAccount(activity)?.let {
        Fitness.getSessionsClient(activity, it)
            .readSession(readRequest)
            .addOnSuccessListener { response ->
                // Use response data here
                Log.d(logTag, "getSessions found ${response.sessions.size} sessions")
                response.sessions.forEach { session ->
                    Log.d(logTag, "Session name: ${session.name}")
                    Log.d(logTag, "Session description: ${session.description}")
                    Log.d(logTag, "Activity type: ${session.activity}")
                    response.getDataSet(session).forEach { dataSet ->
                        dataSet.dataPoints.forEach { dataPoint ->
                            printDataPoint(dataPoint)
                        }
                    }
                }
            }
            .addOnFailureListener { e -> Log.d(logTag, "OnFailure()", e) }
    }
}

fun getBikingSessions(activity: Activity, start: Long, end: Long) {
    val readRequest = SessionReadRequest.Builder()
        //.read(DataType.TYPE_HEIGHT)
        //.read(DataType.TYPE_WEIGHT)
        //.read(DataType.TYPE_SPEED)
        .read(DataType.TYPE_DISTANCE_DELTA)
        .read(DataType.TYPE_HEART_RATE_BPM)
        //.read(DataType.AGGREGATE_HEART_RATE_SUMMARY)
        //.read(DataType.AGGREGATE_HEIGHT_SUMMARY)
        //.read(DataType.AGGREGATE_WEIGHT_SUMMARY)
        .readSessionsFromAllApps()
        .enableServerQueries()
        .excludePackage("com.kvl.cyclotrack.dev")
        .excludePackage("com.kvl.cyclotrack")
        .setTimeInterval(start, end, TimeUnit.SECONDS)
        .build()
    getGoogleAccount(activity)?.let {
        Fitness.getSessionsClient(activity, it)
            .readSession(readRequest)
            .addOnSuccessListener { response ->
                // Use response data here
                Log.d(logTag, "getSessions found ${response.sessions.size} sessions")
                response.sessions.forEach { session ->
                    if (session.activity == "biking") {
                        Log.d(logTag, "Session name: ${session.name}")
                        Log.d(logTag, "Session description: ${session.description}")
                        Log.d(logTag, "Activity type: ${session.activity}")
                        response.getDataSet(session).forEach { dataSet ->
                            dataSet.dataPoints.forEach { dataPoint ->
                                printDataPoint(dataPoint)
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e -> Log.d(logTag, "OnFailure()", e) }
    }
}

fun getAggBiometrics(activity: Activity, start: Long, end: Long) {
    val readRequest = DataReadRequest.Builder()
        .aggregate(DataType.TYPE_HEART_RATE_BPM)
        .aggregate(DataType.TYPE_HEIGHT)
        .aggregate(DataType.TYPE_WEIGHT)
        .setTimeRange(start, end, TimeUnit.SECONDS)
        //.setTimeRange(1, end, TimeUnit.SECONDS)
        .bucketByTime(1, TimeUnit.DAYS)
        //.bucketByActivitySegment(30, TimeUnit.MINUTES)
        //.setLimit(1)
        .build()
    getGoogleAccount(activity)?.let {
        Fitness.getHistoryClient(activity, it)
            .readData(readRequest)
            .addOnSuccessListener { response ->
                // Use response data here
                Log.d(logTag, "getAggBiometrics")
                response.buckets.forEach { bucket ->
                    bucket.dataSets.forEach { dataset ->
                        dataset.dataPoints.forEach { dataPoint ->
                            printDataPoint(dataPoint)
                        }
                    }
                }
            }
            .addOnFailureListener { e -> Log.d(logTag, "OnFailure()", e) }
    }
}

fun accessGoogleFit(activity: Activity) {
    Log.d(logTag, "accessGoogleFit()")
    //val end = System.currentTimeMillis() / 1000
    //val start = end - 60 * 60 * 24 * 30

    val startCal = GregorianCalendar.getInstance()
    val endCal = GregorianCalendar.getInstance()
    startCal.set(2020, 7, 1)
    endCal.set(2020, 9, 30)

    //getActivities(activity,start,end)
    //getAggBiometrics(activity, start, end)
    //getBiometricsHistory(activity, start, end)/get
    //getSession(activity, startCal.timeInMillis/1000, endCal.timeInMillis/1000)
    //getSession(activity, start, end)

    /*
    var inc = startCal
    while (inc < endCal) {
        val rangeStart = inc
        val rangeEnd = inc.clone() as GregorianCalendar
        rangeEnd.add(Calendar.DAY_OF_MONTH, 7)
        getBikingSessions(activity, rangeStart.timeInMillis / 1000, rangeEnd.timeInMillis / 1000)
        inc = rangeEnd
    }
    */

    //getBikingSessions(activity, startCal.timeInMillis / 1000, endCal.timeInMillis / 1000)
    getLatest(activity, DataType.TYPE_WEIGHT)
    getLatest(activity, DataType.TYPE_HEIGHT)
    getLatestHeartRate(activity)
}

fun configureGoogleFit(activity: Activity) {
    with(activity) {
        if (!GoogleSignIn.hasPermissions(getGoogleAccount(this), fitnessOptions)) {
            Log.d(logTag, "Syncing with Google Fit")
            GoogleSignIn.requestPermissions(this,
                1,
                getGoogleAccount(this),
                fitnessOptions)
        } else {
            Log.d(logTag, "Already logged in to Google Fit")
        }
    }
}