package com.kvl.cyclotrack

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import java.util.concurrent.TimeUnit

private val logTag = "GOOGLE_FIT_UTILITIES"

val fitnessOptions = FitnessOptions.builder()
    .addDataType(DataType.AGGREGATE_HEIGHT_SUMMARY,
        FitnessOptions.ACCESS_READ)
    .addDataType(DataType.AGGREGATE_WEIGHT_SUMMARY,
        FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_LOCATION_SAMPLE, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_CYCLING_WHEEL_REVOLUTION, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_CYCLING_WHEEL_RPM, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_CYCLING_PEDALING_CADENCE, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_WEIGHT, FitnessOptions.ACCESS_READ)
    .addDataType(DataType.TYPE_ACTIVITY_SEGMENT,
        FitnessOptions.ACCESS_WRITE)
    .addDataType(DataType.AGGREGATE_ACTIVITY_SUMMARY,
        FitnessOptions.ACCESS_READ)
    .build()

fun getGoogleAccount(context: Context) = GoogleSignIn.getLastSignedInAccount(context)

fun accessGoogleFit(activity: Activity) {
    Log.d(logTag, "accessGoogleFit()")
    val end = System.currentTimeMillis() / 1000
    //val endCal = GregorianCalendar.getInstance()
    //endCal.set(2020, 9, 15)
    //val end = endCal.timeInMillis / 1000
    val start = end - 60 * 60 * 24 * 365

    val readRequest = DataReadRequest.Builder()
        //.aggregate(DataType.AGGREGATE_ACTIVITY_SUMMARY)
        //.read(DataType.AGGREGATE_ACTIVITY_SUMMARY)
        .read(DataType.TYPE_WEIGHT)
        //.read(DataType.TYPE_LOCATION_SAMPLE) cannot be read
        //.read(DataType.TYPE_HEART_RATE_BPM)
        .setTimeRange(start, end, TimeUnit.SECONDS)
        .bucketByTime(24 * 7, TimeUnit.HOURS)
        .build()
    Fitness.getHistoryClient(activity, getGoogleAccount(activity))
        .readData(readRequest)
        .addOnSuccessListener { response ->
            // Use response data here
            Log.d(logTag, "OnSuccess()")
            Log.d(logTag, "DataSet: ${response.getDataSet(DataType.AGGREGATE_ACTIVITY_SUMMARY)}")
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

fun configureGoogleFit(activity: Activity) {
    with(activity) {
        if (getGoogleAccount(this) == null) {
            Log.d(logTag, "Google account is null")
        } else {
            Log.d(logTag, "Google account is valid")
        }

        if (!GoogleSignIn.hasPermissions(getGoogleAccount(this), fitnessOptions)) {
            Log.d(logTag, "Syncing with Google Fit")
            GoogleSignIn.requestPermissions(this,
                1,
                getGoogleAccount(this),
                fitnessOptions)
        } else {
            Log.d(logTag, "Already logged in to Google Fit")
            accessGoogleFit(this)
        }
    }
}
