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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun getDistance(
    curr: CriticalMeasurements,
    prev: CriticalMeasurements,
): Float {
    val distanceArray = floatArrayOf(0f)
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
    return when {
        value < 1.0 -> {
            "zero seconds"
        }
        value < 60 -> {
            "${value.roundToInt()} sec"
        }
        value < 3600 -> {
            val minutes = value / 60
            val minutePart = minutes.toLong()
            val seconds = (minutes - minutePart) * 60
            val secondPart = seconds.toLong()
            "${minutePart}m ${secondPart}s"
        }
        else -> {
            val hours = value / 3600
            val hourPart = hours.toLong()
            val minutes = (hours - hourPart) * 60
            val minutePart = minutes.toLong()
            val seconds = (minutes - minutePart) * 60
            val secondPart = seconds.roundToInt()
            "${hourPart}h ${minutePart}m ${secondPart}s"
        }
    }
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

fun getInspiringMessage(duration: Long): String {
    return when (val days = duration / 24 / 3600000.0) {
        in 0.0..0.5 -> "Great job! Keep up the good work!"
        in 0.5..1.5 -> "Get some rest you earned it! Recovery is an important part of fitness."
        in 1.5..3.0 -> "Alright! Let's hit the trail!"
        else -> "It has been ${
            days.toInt()
        } days since your last ride. Let's make it happen!"
    }
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
    measurements: Array<CriticalMeasurements>? = null,
): Array<LongRange> {
    val intervals = ArrayList<LongRange>()
    timeStates?.forEachIndexed { index, timeState ->
        if (!isTripInProgress(timeState.state) && index > 0 && isTripInProgress(timeStates[index - 1].state)) {
            intervals.add(LongRange(timeStates[index - 1].timestamp, timeState.timestamp))
        }
    }
    if (!timeStates.isNullOrEmpty() && isTripInProgress(timeStates.last().state) && !measurements.isNullOrEmpty()) {
        if (timeStates.last().timestamp < measurements.last().time) {
            intervals.add(LongRange(timeStates.last().timestamp, measurements.last().time))
        }
    }
    return if (intervals.isEmpty() and !measurements.isNullOrEmpty()) {
        arrayOf(LongRange(measurements!!.first().time,
            measurements.last().time))
    } else intervals.toTypedArray()
}

fun getTripLegs(
    measurements: Array<CriticalMeasurements>,
    intervals: Array<LongRange>,
): Array<Array<CriticalMeasurements>> {
    val legs = ArrayList<Array<CriticalMeasurements>>()
    intervals.forEach { interval ->
        legs.add(measurements.filter { interval.contains(it.time) }.toTypedArray())
    }
    return legs.toTypedArray()
}

fun getTripLegs(
    measurements: Array<CriticalMeasurements>,
    timeStates: Array<TimeState>?,
): Array<Array<CriticalMeasurements>> {
    val intervals = getTripIntervals(timeStates, measurements)
    return getTripLegs(measurements, intervals)
}

suspend fun plotPath(
    measurements: Array<CriticalMeasurements>,
    timeStates: Array<TimeState>?,
): MapPath =
    withContext(Dispatchers.Default) {
        val paths = ArrayList<PolylineOptions>()
        var northeastLat = -91.0
        var northeastLng = -181.0
        var southwestLat = 91.0
        var southwestLng = 181.0

        var totalDistance = 0.0
        var lastLat = 0.0
        var lastLng = 0.0

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

        if (isTripInProgress(currTimeState()?.state)) {
            if (lastLat != 0.0 && lastLng != 0.0) {
                val distanceArray = floatArrayOf(0f)
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
        }
    }
    var bounds: LatLngBounds? = null
    try {
        bounds =
            LatLngBounds(LatLng(southwestLat, southwestLng), LatLng(northeastLat, northeastLng))
    } catch (err: IllegalArgumentException) {
        if (measurements.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            var included = false

            measurements.forEach {
                    boundsBuilder.include(LatLng(it.latitude, it.longitude))
                    included = true
            }
            if (included) bounds = boundsBuilder.build()
        }
        Log.d("PLOT_PATH",
            String.format("Bounds could not be calculated: path size = %d", measurements.size))
    }
        return@withContext MapPath(paths.toTypedArray(), bounds)
    }

const val METERS_TO_FEET = 3.28084
const val FEET_TO_METERS = 1 / METERS_TO_FEET
const val METERS_TO_KM = 0.001
const val METERS_TO_MM = 1000.0
const val FEET_TO_MILES = 1.0 / 5280
const val SECONDS_TO_HOURS = 1.0 / 3600
const val INCHES_TO_FEET = 1 / 12.0
const val FEET_TO_INCHES = 12.0

fun getBrightnessPreference(context: Context): Float {
    return if (PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.preferences_dashboard_brightness_toggle_key),
                true)
    ) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .getInt(context.getString(R.string.preferences_dashboard_brightness_key), 50) / 100f
    } else -1f
}

fun getUserSpeed(context: Context, meters: Double, seconds: Double): Float =
    getUserSpeed(context, meters / seconds)

fun getUserSpeed(context: Context, speed: Double): Float =
    getUserSpeed(context, speed.toFloat())

fun getUserSpeed(context: Context, speed: Float): Float {
    val userConversionFactor =
        when (PreferenceManager.getDefaultSharedPreferences(context)
            .getString("display_units", "1")) {
            "1" -> METERS_TO_FEET * FEET_TO_MILES / SECONDS_TO_HOURS
            "2" -> METERS_TO_KM / SECONDS_TO_HOURS
            else -> 1.0
        }
    return (speed * userConversionFactor).toFloat()
}

fun getUserDistance(context: Context, meters: Double): Double {
    val userConversionFactor =
        when (PreferenceManager.getDefaultSharedPreferences(context)
            .getString("display_units", "1")) {
            "1" -> METERS_TO_FEET * FEET_TO_MILES
            "2" -> METERS_TO_KM
            else -> 1.0
        }
    return meters * userConversionFactor
}

fun getUserLength(context: Context, meters: Double): Double {
    val userConversionFactor =
        when (PreferenceManager.getDefaultSharedPreferences(context)
            .getString("display_units", "1")) {
            "1" -> METERS_TO_FEET * FEET_TO_INCHES
            "2" -> METERS_TO_MM
            else -> 1.0
        }
    return meters * userConversionFactor
}

fun getUserCircumference(context: Context): Float = getUserCircumferenceOrNull(context) ?: 0f

fun getUserCircumferenceOrNull(context: Context): Float? {
    return getUserCircumferenceOrNull(PreferenceManager.getDefaultSharedPreferences(context))
}

fun getUserCircumferenceOrNull(prefs: SharedPreferences): Float? {
    val storedCircumference = prefs.getString("wheel_circumference", "2037")
    Log.d("TRIP_UTILS_PREF", "Wheel circumference preference: ${storedCircumference}")
    return userCircumferenceToMeters(storedCircumference)
}

fun metersToUserCircumference(context: Context, meters: Float): String {
    return metersToUserCircumference(meters, PreferenceManager.getDefaultSharedPreferences(context))
}

fun metersToUserCircumference(meters: Float, prefs: SharedPreferences): String {
    val storedCircumference = prefs.getString("wheel_circumference", "2037")
    return metersToUserCircumference(meters, storedCircumference)
}

fun metersToUserCircumference(meters: Float, storedCircumference: String?): String {
    return try {
        return when (storedCircumference?.toFloat() ?: Float.NEGATIVE_INFINITY) {
            in 0.9f..10f -> {
                //meters
                String.format("%.3f", meters)
            }
            in 30f..120f -> {
                //inches
                String.format("%.2f", (meters * METERS_TO_FEET * FEET_TO_INCHES))
            }
            in 900f..10000f -> {
                //mm
                (meters * 1000f).toInt().toString()
            }
            else -> String.format("%.3f", meters)
        }
    } catch (e: NumberFormatException) {
        Log.e("TRIP_UTILS_PREF", "Couldn't parse wheel circumference")
        String.format("%.3f", meters)
    }
}

fun userCircumferenceToMeters(input: String?): Float? {
    return try {
        return when (val circumference =
            input?.toFloat() ?: Float.NEGATIVE_INFINITY) {
            in 0.9f..10f -> {
                //meters
                circumference
            }
            in 30f..120f -> {
                //inches
                (circumference * INCHES_TO_FEET * FEET_TO_METERS).toFloat()
            }
            in 900f..10000f -> {
                //mm
                circumference / 1000f
            }
            else -> null
        }
    } catch (e: NumberFormatException) {
        Log.e("TRIP_UTILS_PREF", "Couldn't parse wheel circumference")
        null
    }
}

fun getUserAltitude(context: Context, meters: Double): Double {
    val userConversionFactor =
        when (PreferenceManager.getDefaultSharedPreferences(context)
            .getString("display_units", "1")) {
            "1" -> METERS_TO_FEET
            else -> 1.0
        }
    return meters * userConversionFactor
}

fun getUserDistanceUnitShort(context: Context): String {
    return when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "1")) {
        "1" -> "mi"
        "2" -> "km"
        else -> "mi"
    }
}

fun getUserDistanceUnitLong(context: Context): String {
    return when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "1")) {
        "1" -> "miles"
        "2" -> "kilometers"
        else -> "miles"
    }
}

fun getUserSpeedUnitShort(context: Context): String {
    return when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "1")) {
        "1" -> "mph"
        "2" -> "km/h"
        else -> "mph"
    }
}

fun getUserSpeedUnitLong(context: Context): String {
    return when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "1")) {
        "1" -> "miles per hour"
        "2" -> "kilometers per hour"
        else -> "miles per hour"
    }
}

fun getUserAltitudeUnitShort(context: Context): String {
    return when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "1")) {
        "1" -> "ft"
        "2" -> "m"
        else -> "ft"
    }
}

fun getUserAltitudeUnitLong(context: Context): String {
    return when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "1")) {
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
    return getSplitThreshold(prefs.getString("display_units", "1"))
}

fun crossedSplitThreshold(
    prefs: SharedPreferences,
    newDistance: Double,
    oldDistance: Double,
): Boolean {
    val userConversionFactor =
        when (prefs.getString("display_units", "1")) {
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
    tripId: Long,
    measurements: Array<CriticalMeasurements>,
    timeStates: Array<TimeState>?,
    sharedPreferences: SharedPreferences,
): ArrayList<Split> {
    val tripSplits = arrayListOf<Split>()
    var totalDistance = 0.0
    var totalActiveTime: Double = 0.0
    var timestamp: Long = 0

    if (measurements.isNullOrEmpty()) return tripSplits

    val intervals = getTripIntervals(timeStates, measurements)
    if (intervals.isNullOrEmpty()) return tripSplits

    val legs = getTripLegs(measurements, intervals)
    if (legs.isNullOrEmpty()) return tripSplits

    legs.forEachIndexed { legIdx, leg ->
        if (leg.isNullOrEmpty()) return@forEachIndexed
        var prev = leg[0]
        for (measurementIdx in 1 until leg.size) {
            val lastSplit = getLastSplit(tripSplits)
            val curr = leg[measurementIdx]

            totalDistance += getDistance(curr, prev)
            totalActiveTime =
                ((curr.time - (intervals[legIdx].first)) / 1e3) + accumulateTripTime(intervals.sliceArray(
                    IntRange(0, legIdx - 1)))
            timestamp = curr.time

            if (crossedSplitThreshold(sharedPreferences,
                    totalDistance,
                    lastSplit.totalDistance)
            ) {
                tripSplits.add(makeSplit(tripId,
                    totalDistance,
                    totalActiveTime,
                    lastSplit,
                    timestamp))
            }
            prev = curr
        }
    }
    val lastSplit = getLastSplit(tripSplits)
    if (timestamp != lastSplit.timestamp) {
        tripSplits.add(makeSplit(tripId,
            totalDistance,
            totalActiveTime,
            lastSplit,
            timestamp))
    }
    return tripSplits
}

fun getLastSplit(tripSplits: ArrayList<Split>) =
    if (tripSplits.isEmpty()) Split(0,
        0.0,
        0.0,
        0.0,
        0.0,
        0,
        0) else tripSplits.last()

fun makeSplit(
    tripId: Long,
    totalDistance: Double,
    totalActiveTime: Double,
    lastSplit: Split,
    timestamp: Long,
): Split {
    return Split(timestamp = timestamp,
        duration = totalActiveTime - lastSplit.totalDuration,
        distance = totalDistance - lastSplit.totalDistance,
        totalDuration = totalActiveTime,
        totalDistance = totalDistance,
        tripId = tripId)
}

fun getDifferenceRollover(newTime: Int, oldTime: Int, rollover: Int = 65535) =
    newTime + when (oldTime > newTime) {
        true -> rollover
        else -> 0
    } - oldTime


fun getRpm(rev: Int, revLast: Int, time: Int, timeLast: Int): Float {
    //NOTE: Does not handle 32-bit rollover, as the CSC spec states 32-bit values
    //do not rollover.
    return getDifferenceRollover(rev, revLast).toFloat().let {
        if (it == 0f) 0f else it / getDifferenceRollover(time,
            timeLast) * 1024 * 60
    }
}

fun getGattUuid(uuid: String): UUID {
    val gattUuidSuffix = "0000-1000-8000-00805f9b34fb"
    return UUID.fromString("$uuid-$gattUuidSuffix")
}

data class MapPath(val paths: Array<PolylineOptions>, val bounds: LatLngBounds?)