package com.kvl.cyclotrack

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ActivityScoped
class GoogleFitApiService @Inject constructor(@ActivityContext private val context: Context) {
    private val logTag = "GoogleFitApiService"
    private val activity = context as Activity
    var weight = MutableLiveData(0f)
    var height = MutableLiveData(0f)
    var heartRate = MutableLiveData(0)

    fun getLatestHeight(
        timestamp: Long = System.currentTimeMillis(),
    ): LiveData<Float> {
        Fitness.getHistoryClient(activity, getGoogleAccount(activity))
            .readData(DataReadRequest.Builder().read(DataType.TYPE_HEIGHT).setLimit(1)
                .setTimeRange(1, timestamp, TimeUnit.MILLISECONDS).build())
            .addOnSuccessListener { response ->
                // Use response data here
                Log.d(logTag, "getLatestWeight")
                response.dataSets.forEach { dataset ->
                    dataset.dataPoints.forEach { dataPoint ->
                        when (dataPoint.dataType) {
                            DataType.TYPE_HEIGHT -> {
                                height.value = dataPoint.getValue(Field.FIELD_HEIGHT).asFloat()
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.d(logTag, "OnFailure()", e)
                height.value = null
            }
        return height
    }

    fun getLatestWeight(
        timestamp: Long = System.currentTimeMillis(),
    ): LiveData<Float> {
        Fitness.getHistoryClient(activity, getGoogleAccount(activity))
            .readData(DataReadRequest.Builder().read(DataType.TYPE_WEIGHT).setLimit(1)
                .setTimeRange(1, timestamp, TimeUnit.MILLISECONDS).build())
            .addOnSuccessListener { response ->
                // Use response data here
                Log.d(logTag, "getLatestWeight")
                response.dataSets.forEach { dataset ->
                    dataset.dataPoints.forEach { dataPoint ->
                        when (dataPoint.dataType) {
                            DataType.TYPE_WEIGHT -> {
                                weight.value = dataPoint.getValue(Field.FIELD_WEIGHT).asFloat()
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.d(logTag, "OnFailure()", e)
                weight.value = null
            }
        return weight
    }

    fun getLatestRestingHeartRate(timestamp: Long = System.currentTimeMillis()): LiveData<Int> {
        val end = timestamp / 1000
        val start = end - 60 * 60 * 24 * 30
        Fitness.getHistoryClient(activity, getGoogleAccount(activity))
            .readData(DataReadRequest.Builder().aggregate(DataType.TYPE_HEART_RATE_BPM)
                .bucketByTime(30, TimeUnit.DAYS).setLimit(1)
                .setTimeRange(start, end, TimeUnit.SECONDS).build())
            .addOnSuccessListener { response ->
                Log.d(logTag, "getLatestHeartRate")
                response.buckets.forEach { bucket ->
                    bucket.dataSets.forEach { dataset ->
                        dataset.dataPoints.forEach { dataPoint ->
                            heartRate.value = dataPoint.getValue(Field.FIELD_MIN).asFloat().toInt()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.d(logTag, "OnFailure()", e)
                heartRate.value = null
            }
        return heartRate
    }

    fun hasPermission(): Boolean =
        GoogleSignIn.hasPermissions(getGoogleAccount(context), fitnessOptions)
}