package com.kvl.cyclotrack

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
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

fun getDistance(
    curr: Measurements,
    prev: Measurements,
): Float {
    val distanceArray = floatArrayOf(0f)
    Location.distanceBetween(curr.latitude,
        curr.longitude,
        prev.latitude,
        prev.longitude,
        distanceArray)
    return distanceArray[0]
}

fun getDistance(
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

        fun update(a: A?, b: B?) {
            if (a != null && b != null)
                value = Pair(a, b)
        }

        addSource(a) {
            lastA = it
            update(lastA, lastB)
        }
        addSource(b) {
            lastB = it
            update(lastA, lastB)
        }
    }
}

fun <A, B, C> zipLiveData(
    a: LiveData<A>,
    b: LiveData<B>,
    c: LiveData<C>,
): LiveData<Triple<A, B, C>> {
    return MediatorLiveData<Triple<A, B, C>>().apply {
        var lastA: A? = null
        var lastB: B? = null
        var lastC: C? = null

        fun update(a: A?, b: B?, c: C?) {
            if (a != null && b != null && c != null)
                value = Triple(a, b, c)
        }

        addSource(a) {
            lastA = it
            update(lastA, lastB, lastC)
        }
        addSource(b) {
            lastB = it
            update(lastA, lastB, lastC)
        }
        addSource(c) {
            lastC = it
            update(lastA, lastB, lastC)
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

fun accumulateActiveTime(timeStates: Array<TimeState>) =
    accumulateTripTime(getTripIntervals(timeStates))

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

fun getTripInProgressIntervals(
    timeStates: Array<TimeState>?,
): Array<LongRange> {
    val intervals = ArrayList<LongRange>()
    var intervalStart = -1L
    timeStates?.forEach { timeState ->
        if (isTripInProgress(timeState.state) && intervalStart < 0) intervalStart =
            timeState.timestamp
        if (intervalStart >= 0 && !isTripInProgress(timeState.state)) {
            intervals.add(LongRange(intervalStart, timeState.timestamp))
            intervalStart = -1L
        }
    }
    if (!timeStates.isNullOrEmpty() && isTripInProgress(timeStates.last().state)) {
        intervals.add(LongRange(timeStates.last().timestamp, System.currentTimeMillis()))
    }
    return intervals.toTypedArray()
}

fun getTripIntervals(
    timeStates: Array<TimeState>?,
    measurements: Array<CriticalMeasurements>? = null,
): Array<LongRange> =
    when (measurements.isNullOrEmpty()) {
        true -> getTripIntervals(timeStates, null, null)
        false -> getTripIntervals(timeStates, measurements.first(), measurements.last())
    }

fun getTripIntervals(
    timeStates: Array<TimeState>?,
    firstMeasurement: CriticalMeasurements? = null,
    lastMeasurement: CriticalMeasurements? = null,
): Array<LongRange> {
    val intervals = ArrayList<LongRange>()
    var intervalStart = -1L
    timeStates?.forEach { timeState ->
        if (isTripInProgress(timeState.state) && intervalStart < 0) intervalStart =
            timeState.timestamp
        if (intervalStart >= 0 && !isTripInProgress(timeState.state)) {
            intervals.add(LongRange(intervalStart, timeState.timestamp))
            intervalStart = -1L
        }
    }
    if (!timeStates.isNullOrEmpty() && isTripInProgress(timeStates.last().state) && lastMeasurement != null) {
        if (timeStates.last().timestamp < lastMeasurement.time) {
            intervals.add(LongRange(timeStates.last().timestamp, lastMeasurement.time))
        }
    }
    return if (intervals.isEmpty() && firstMeasurement != null && lastMeasurement != null) {
        arrayOf(LongRange(firstMeasurement.time,
            lastMeasurement.time))
    } else intervals.toTypedArray()
}

fun getStartTime(timeStates: Array<TimeState>) =
    timeStates.find { it.state == TimeStateEnum.START }?.timestamp

fun getEndTime(timeStates: Array<TimeState>) =
    timeStates.find { it.state == TimeStateEnum.STOP }?.timestamp

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

fun getEffectiveCircumference(trip: Trip, measurements: Array<CriticalMeasurements>) =
    trip.distance?.let { distance ->
        measurements.filter { meas -> meas.speedRevolutions != null }
            .map { filtered -> filtered.speedRevolutions!! }
            .let { mapped ->
                if (mapped.isNotEmpty()) {
                    distance.div(mapped.last()
                        .minus(mapped.first().toDouble()))
                        .toFloat()
                } else null
            }
    }

suspend fun plotPath(
    measurements: Array<CriticalMeasurements>,
    timeStates: Array<TimeState>?,
): MapPath =
    withContext(Dispatchers.IO) {
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

fun getUserSpeed(context: Context, meters: Double, seconds: Double): Float =
    getUserSpeed(context, meters / seconds)

fun getUserSpeed(context: Context, speed: Double): Float =
    getUserSpeed(context, speed.toFloat())

fun getUserSpeed(context: Context, speed: Float): Float {
    val userConversionFactor =
        when (getSystemOfMeasurement(context)) {
            "1" -> METERS_TO_FEET * FEET_TO_MILES / SECONDS_TO_HOURS
            "2" -> METERS_TO_KM / SECONDS_TO_HOURS
            else -> 1.0
        }
    return (speed * userConversionFactor).toFloat()
}

fun getUserDistance(context: Context, meters: Double): Double {
    val userConversionFactor =
        when (getSystemOfMeasurement(context)) {
            "1" -> METERS_TO_FEET * FEET_TO_MILES
            "2" -> METERS_TO_KM
            else -> 1.0
        }
    return meters * userConversionFactor
}

fun getUserLength(context: Context, meters: Double): Double {
    val userConversionFactor =
        when (getSystemOfMeasurement(context)) {
            "1" -> METERS_TO_FEET * FEET_TO_INCHES
            "2" -> METERS_TO_MM
            else -> 1.0
        }
    return meters * userConversionFactor
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
        when (getSystemOfMeasurement(context)) {
            "1" -> METERS_TO_FEET
            else -> 1.0
        }
    return meters * userConversionFactor
}

fun getUserDistanceUnitShort(context: Context): String {
    return when (getSystemOfMeasurement(context)) {
        "1" -> "mi"
        "2" -> "km"
        else -> "mi"
    }
}

fun getUserDistanceUnitLong(context: Context): String {
    return when (getSystemOfMeasurement(context)) {
        "1" -> "miles"
        "2" -> "kilometers"
        else -> "miles"
    }
}

fun getUserSpeedUnitShort(context: Context): String {
    return when (getSystemOfMeasurement(context)) {
        "1" -> "mph"
        "2" -> "km/h"
        else -> "mph"
    }
}

fun getUserSpeedUnitLong(context: Context): String {
    return when (getSystemOfMeasurement(context)) {
        "1" -> "miles per hour"
        "2" -> "kilometers per hour"
        else -> "miles per hour"
    }
}

fun getUserAltitudeUnitShort(context: Context): String {
    return when (getSystemOfMeasurement(context)) {
        "1" -> "ft"
        "2" -> "m"
        else -> "ft"
    }
}

fun getUserAltitudeUnitLong(context: Context): String {
    return when (getSystemOfMeasurement(context)) {
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
    return crossedSplitThreshold(getPreferences(context),
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

fun getDifferenceRollover(new: Int, old: Int, rollover: Int = 65536) =
    new + when (old > new) {
        true -> rollover
        else -> 0
    } - old


fun getRpm(rev: Int, revLast: Int, time: Int, timeLast: Int, delta: Long): Float {
    //NOTE: Does not handle 32-bit rollover, as the CSC spec states 32-bit values
    //do not rollover.
    //Accounts for gaps in samples larger than BLE time range
    val rollCount = (delta / 64000).toInt()
    val adjustedTime = time + 65536 * rollCount
    return getDifferenceRollover(rev, revLast).toFloat().let {
        if (it == 0f) 0f else it / getDifferenceRollover(adjustedTime,
            timeLast) * 1024 * 60
    }
}

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

fun degreesToCardinal(degrees: Float): String {
    //val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    return when (degrees) {
        in 0f..11.25f -> "N"
        in 11.25f..33.75f -> "NNE"
        in 33.75f..56.25f -> "NE"
        in 56.25f..78.75f -> "ENE"
        in 78.75f..101.25f -> "E"
        in 101.25f..123.75f -> "ESE"
        in 123.75f..145.25f -> "SE"
        in 145.25f..167.75f -> "SSE"
        in 167.75f..190.25f -> "S"
        in 190.25f..212.75f -> "SSW"
        in 212.75f..235.25f -> "SW"
        in 235.25f..257.75f -> "WSW"
        in 257.75f..281.25f -> "W"
        in 281.25f..303.75f -> "WNW"
        in 303.75f..326.25f -> "NW"
        in 326.25f..348.75f -> "NNW"
        in 348.75f..360.0f -> "N"
        else -> "UNK"

    }
}

fun validateCadence(current: CriticalMeasurements, previous: CriticalMeasurements): Boolean {
    val cadenceDidNotUpdate = current.cadenceLastEvent == previous.cadenceLastEvent
    val doubleRollover =
        (current.cadenceLastEvent!! < previous.cadenceLastEvent!! && current.cadenceRevolutions!! < previous.cadenceRevolutions!!)
    val prematureRollover =
        (previous.cadenceRevolutions!! < 65500f && current.cadenceRevolutions!! < previous.cadenceRevolutions!!)
    val veryPrematureRollover =
        (previous.cadenceRevolutions!! < 64000f && current.cadenceRevolutions!! < previous.cadenceRevolutions!!)
    val deviceReset = prematureRollover && doubleRollover

    return !(cadenceDidNotUpdate || deviceReset || veryPrematureRollover)
}

data class MapPath(val paths: Array<PolylineOptions>, val bounds: LatLngBounds?)