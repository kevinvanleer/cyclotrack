package com.kvl.cyclotrack

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt

@ActivityScoped
class GoogleFitApiService @Inject constructor(@ActivityContext private val context: Context) {
    private val logTag = "GoogleFitApiService"
    private val activity = context as Activity

    suspend fun getLatestHeight(
        timestamp: Long = System.currentTimeMillis(),
    ): Float? {
        var height: Float? = null
        Fitness.getHistoryClient(activity, getGoogleAccount(activity))
            .readData(DataReadRequest.Builder().read(DataType.TYPE_HEIGHT).setLimit(1)
                .setTimeRange(1, timestamp, TimeUnit.MILLISECONDS).build()).await()
            .dataSets.forEach { dataset ->
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
        Fitness.getHistoryClient(activity, getGoogleAccount(activity))
            .readData(DataReadRequest.Builder().read(DataType.TYPE_WEIGHT).setLimit(1)
                .setTimeRange(1, timestamp, TimeUnit.MILLISECONDS).build()).await()
            .dataSets.forEach { dataset ->
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
        Fitness.getHistoryClient(activity, getGoogleAccount(activity))
            .readData(DataReadRequest.Builder().aggregate(DataType.TYPE_HEART_RATE_BPM)
                .bucketByTime(30, TimeUnit.DAYS).setLimit(1)
                .setTimeRange(start, end, TimeUnit.SECONDS).build()).await()
            .buckets.forEach { bucket ->
                bucket.dataSets.forEach { dataset ->
                    dataset.dataPoints.forEach { dataPoint ->
                        hr = dataPoint.getValue(Field.FIELD_MIN).asFloat().roundToInt()
                    }
                }
            }
        return hr
    }

    fun hasPermission(): Boolean =
        GoogleSignIn.hasPermissions(getGoogleAccount(context), fitnessOptions)
}