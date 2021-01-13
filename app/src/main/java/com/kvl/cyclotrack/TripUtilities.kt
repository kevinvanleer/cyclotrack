package com.kvl.cyclotrack

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.preference.PreferenceManager
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun getDistance(
    curr: Measurements,
    prev: Measurements,
): Float {
    var distanceArray = floatArrayOf(0f)
    Location.distanceBetween(curr.latitude,
        curr.longitude,
        prev.latitude,
        prev.longitude,
        distanceArray)
    return distanceArray[0]
}

fun <A, B> zipLiveData(a: LiveData<A>, b: LiveData<B>): LiveData<Pair<A, B>> {
    return MediatorLiveData<Pair<A, B>>().apply {
        var lastA: A? = null
        var lastB: B? = null

        fun update() {
            val localLastA = lastA
            val localLastB = lastB
            if (localLastA != null && localLastB != null)
                this.value = Pair(localLastA, localLastB)
        }

        addSource(a) {
            lastA = it
            update()
        }
        addSource(b) {
            lastB = it
            update()
        }
    }
}

fun formatDuration(value: Double): String {
    var formattedString = ""
    if (value < 1.0) {
        formattedString += "zero seconds"
    } else if (value < 60) {
        formattedString += "${value.roundToInt()} sec"
    } else if (value < 3600) {
        val minutes = value / 60
        val minutePart = minutes.toLong()
        val seconds = (minutes - minutePart) * 60
        val secondPart = seconds.toLong()
        formattedString += "${minutePart}m ${secondPart}s"
    } else {
        val hours = value / 3600
        val hourPart = hours.toLong()
        val minutes = (hours - hourPart) * 60
        val minutePart = minutes.toLong()
        val seconds = (minutes - minutePart) * 60
        val secondPart = seconds.roundToInt()
        formattedString += "${hourPart}h ${minutePart}m ${secondPart}s"
    }
    return formattedString
}

fun isTripInProgress(state: TimeStateEnum?) =
    state == null || state == TimeStateEnum.RESUME || state == TimeStateEnum.START

fun accumulateTripPauses(intervals: Array<LongRange>): Double {
    var sum = 0L
    for (idx in 1 until intervals.size) {
        sum += intervals[idx].first - intervals[idx - 1].last
    }
    return sum * 1e-3
}

fun accumulateTripTime(intervals: Array<LongRange>): Double {
    var sum = 0L
    intervals.forEach { interval ->
        sum += interval.last - interval.first
    }
    return sum * 1e-3
}

fun accumulateTime(intervals: Array<LongRange>): Double {
    return if (intervals.size <= 1) 0.0 else accumulateTripTime(intervals.sliceArray(IntRange(0,
        intervals.size - 2))) + accumulateTripPauses(intervals)
}

fun accumulatedTime(timeStates: Array<TimeState>?): Double {
    //TODO: Memoize
    var sum = 0L
    timeStates?.forEachIndexed { idx, timeState ->
        if (!isTripInProgress(timeState.state)) {
            sum += timeState.timestamp - timeStates[idx - 1].timestamp
        }
    }
    return sum * 1e-3
}

fun getTripIntervals(
    timeStates: Array<TimeState>?,
    measurements: Array<Measurements>? = null,
): Array<LongRange> {
    var intervals = ArrayList<LongRange>()
    timeStates?.forEachIndexed { index, timeState ->
        if (!isTripInProgress(timeState.state) && index > 0 && isTripInProgress(timeStates[index - 1].state)) {
            intervals.add(LongRange(timeStates[index - 1].timestamp, timeState.timestamp))
        }
    }
    if (!timeStates.isNullOrEmpty() && isTripInProgress(timeStates?.last()?.state) && !measurements.isNullOrEmpty()) {
        if (timeStates!!.last().timestamp < measurements!!.last().time) {
            intervals.add(LongRange(timeStates!!.last().timestamp, measurements!!.last().time))
        }
    }
    return if (intervals.isEmpty() and !measurements.isNullOrEmpty()) {
        arrayOf(LongRange(measurements!!.first().time,
            measurements.last().time))
    } else intervals.toTypedArray()
}

fun getTripLegs(
    measurements: Array<Measurements>,
    intervals: Array<LongRange>,
): Array<Array<Measurements>> {
    val legs = ArrayList<Array<Measurements>>()
    intervals.forEach { interval ->
        legs.add(measurements.filter { interval.contains(it.time) }.toTypedArray())
    }
    return legs.toTypedArray()
}

fun getTripLegs(
    measurements: Array<Measurements>,
    timeStates: Array<TimeState>?,
): Array<Array<Measurements>> {
    var intervals = getTripIntervals(timeStates, measurements)
    return getTripLegs(measurements, intervals)
}

fun plotPath(measurements: Array<Measurements>, timeStates: Array<TimeState>?): MapPath {
    val paths = ArrayList<PolylineOptions>()
    var northeastLat = -91.0
    var northeastLng = -181.0
    var southwestLat = 91.0
    var southwestLng = 181.0

    var totalDistance = 0.0
    var lastLat = 0.0
    var lastLng = 0.0

    var maxSpeedAccuracy = 0f
    var accSpeedAccuracy = 0f
    var sampleCount = 0

    var timeStateIdx = 0
    paths.add(PolylineOptions())

    fun currTimeState(): TimeState? {
        return try {
            timeStates?.get(timeStateIdx)
        } catch (e: ArrayIndexOutOfBoundsException) {
            null
        }
    }

    fun nextTimeState(): TimeState? {
        return try {
            timeStates?.get(timeStateIdx + 1)
        } catch (e: ArrayIndexOutOfBoundsException) {
            null
        }
    }

    measurements.forEach {
        while (it.time > nextTimeState()?.timestamp ?: Long.MAX_VALUE) {
            ++timeStateIdx
            if (isTripInProgress(currTimeState()?.state)) {
                paths.add(PolylineOptions())
                lastLat = it.latitude
                lastLng = it.longitude
            }
        }

        if (isTripInProgress(currTimeState()?.state) && it.accuracy < 5) {
            if (lastLat != 0.0 && lastLng != 0.0) {
                var distanceArray = floatArrayOf(0f)
                Location.distanceBetween(lastLat,
                    lastLng,
                    it.latitude,
                    it.longitude,
                    distanceArray)
                totalDistance += distanceArray[0]
            }
            lastLat = it.latitude
            lastLng = it.longitude
            paths.last().add(LatLng(it.latitude, it.longitude))
            northeastLat = max(northeastLat, it.latitude)
            northeastLng = max(northeastLng, it.longitude)
            southwestLat = min(southwestLat, it.latitude)
            southwestLng = min(southwestLng, it.longitude)
            maxSpeedAccuracy = max(maxSpeedAccuracy, it.speedAccuracyMetersPerSecond)
            accSpeedAccuracy += it.speedAccuracyMetersPerSecond
            sampleCount++
        }
    }
    Log.d("TRIP_SUMMARIES_ADAPTER",
        "distance=${totalDistance}, maxSpeedAcc=${maxSpeedAccuracy}, avgSpeedAcc=${accSpeedAccuracy / sampleCount}")
    var bounds: LatLngBounds? = null
    try {
        bounds =
            LatLngBounds(LatLng(southwestLat, southwestLng), LatLng(northeastLat, northeastLng))
    } catch (err: IllegalArgumentException) {
        if (measurements.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            var included = false

            measurements.forEach {
                if (it.accuracy < 5) {
                    boundsBuilder.include(LatLng(it.latitude, it.longitude))
                    included = true
                }
            }
            if (included) bounds = boundsBuilder.build()
        }
        Log.d("PLOT_PATH",
            String.format("Bounds could not be calculated: path size = %d", measurements.size))
    }

    return MapPath(paths.toTypedArray(), bounds)
}

const val METERS_TO_FEET = 3.28084
const val METERS_TO_KM = 0.001
const val FEET_TO_MILES = 1.0 / 5280
const val SECONDS_TO_HOURS = 1.0 / 3600
fun getUserSpeed(context: Context, meters: Double, seconds: Double): Double =
    getUserSpeed(context, meters / seconds)

fun getUserSpeed(context: Context, speed: Double): Double {
    val userConversionFactor =
        when (PreferenceManager.getDefaultSharedPreferences(context)
            .getString("display_units", "US")) {
            "1" -> METERS_TO_FEET * FEET_TO_MILES / SECONDS_TO_HOURS
            "2" -> METERS_TO_KM / SECONDS_TO_HOURS
            else -> 1.0
        }
    Log.d("TRIP_UTILITIES", "Speed conversion factor: $userConversionFactor")
    return speed * userConversionFactor
}

fun getUserDistance(context: Context, meters: Double): Double {
    val userConversionFactor =
        when (PreferenceManager.getDefaultSharedPreferences(context)
            .getString("display_units", "US")) {
            "1" -> METERS_TO_FEET * FEET_TO_MILES
            "2" -> METERS_TO_KM
            else -> 1.0
        }
    return meters * userConversionFactor
}


fun getUserAltitude(context: Context, meters: Double): Double {
    val userConversionFactor =
        when (PreferenceManager.getDefaultSharedPreferences(context)
            .getString("display_units", "US")) {
            "1" -> METERS_TO_FEET
            else -> 1.0
        }
    return meters * userConversionFactor
}

fun getUserDistanceUnitShort(context: Context): String {
    return when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "US")) {
        "1" -> "mi"
        "2" -> "km"
        else -> "mi"
    }
}

fun getUserDistanceUnitLong(context: Context): String {
    return when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "US")) {
        "1" -> "miles"
        "2" -> "kilometers"
        else -> "miles"
    }
}

fun getUserSpeedUnitShort(context: Context): String {
    return when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "US")) {
        "1" -> "mph"
        "2" -> "km/h"
        else -> "mph"
    }
}

fun getUserSpeedUnitLong(context: Context): String {
    return when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "US")) {
        "1" -> "miles per hour"
        "2" -> "kilometers per hour"
        else -> "miles per hour"
    }
}

fun getUserAltitudeUnitShort(context: Context): String {
    return when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "US")) {
        "1" -> "ft"
        "2" -> "m"
        else -> "ft"
    }
}

fun getUserAltitudeUnitLong(context: Context): String {
    return when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "US")) {
        "1" -> "feet"
        "2" -> "meters"
        else -> "feet"
    }
}

fun getSplitThreshold(system: String?): Double {
    return when (system) {
        "1" -> METERS_TO_FEET * FEET_TO_MILES
        "2" -> METERS_TO_KM
        else -> 1.0
    }
}

fun getSplitThreshold(
    prefs: SharedPreferences,
): Double {
    return getSplitThreshold(prefs.getString("display_units", "US"))
}

fun crossedSplitThreshold(
    prefs: SharedPreferences,
    newDistance: Double,
    oldDistance: Double,
): Boolean {
    val userConversionFactor =
        when (prefs.getString("display_units", "US")) {
            "1" -> METERS_TO_FEET * FEET_TO_MILES
            "2" -> METERS_TO_KM
            else -> 1.0
        }
    return floor(newDistance * userConversionFactor) > floor(oldDistance * userConversionFactor)
}

fun crossedSplitThreshold(context: Context, newDistance: Double, oldDistance: Double): Boolean {
    return crossedSplitThreshold(PreferenceManager.getDefaultSharedPreferences(context),
        newDistance,
        oldDistance)
}

fun calculateSplits(
    measurements: Array<Measurements>,
    timeStates: Array<TimeState>?,
    sharedPreferences: SharedPreferences,
): ArrayList<Split> {
    val tripSplits = arrayListOf<Split>()
    var totalDistance = 0.0
    val tripId = measurements[0].tripId
    var totalActiveTime: Double

    var intervals = getTripIntervals(timeStates, measurements)
    val legs = getTripLegs(measurements, intervals)

    legs.forEachIndexed { legIdx, leg ->
        var prev = leg[0]
        for (measurementIdx in 1 until leg.size) {
            val lastSplit = if (tripSplits.isEmpty()) Split(0,
                0.0,
                0.0,
                0.0,
                0.0,
                0,
                0) else tripSplits.last()
            val curr = leg[measurementIdx]


            if (curr.accuracy < 5 && prev.accuracy < 5) {
                totalDistance += getDistance(curr, prev)
                totalActiveTime =
                    ((curr.time - (intervals[legIdx].first)) / 1e3) + accumulateTripTime(intervals.sliceArray(
                        IntRange(0, legIdx - 1)))

                if (crossedSplitThreshold(sharedPreferences,
                        totalDistance,
                        lastSplit.totalDistance)
                ) {
                    val splitDistance = totalDistance - lastSplit.totalDistance
                    val splitDuration = totalActiveTime - lastSplit.totalDuration
                    tripSplits.add(Split(timestamp = curr.time,
                        duration = splitDuration,
                        distance = splitDistance,
                        totalDuration = totalActiveTime,
                        totalDistance = totalDistance,
                        tripId = tripId))
                }
            }
            if (curr.accuracy < 5) prev = curr
        }
    }
    return tripSplits
}


data class MapPath(val paths: Array<PolylineOptions>, val bounds: LatLngBounds?)