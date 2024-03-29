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
import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.DerivedTripState
import com.kvl.cyclotrack.data.HeartRateMeasurement
import com.kvl.cyclotrack.util.SystemUtils
import com.kvl.cyclotrack.util.getPreferences
import com.kvl.cyclotrack.util.getSystemOfMeasurement
import com.kvl.cyclotrack.util.getUserCircumferenceOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val LOCATION_ACCURACY_THRESHOLD = 20f

fun getDistance(
    curr: Measurements,
    prev: Measurements,
): Float {
    val distanceArray = floatArrayOf(0f)
    Location.distanceBetween(
        curr.latitude,
        curr.longitude,
        prev.latitude,
        prev.longitude,
        distanceArray
    )
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

fun formatDurationHours(value: Double): String =
    when (val hours: Double = value / 3600) {
        0.0 -> "0"
        in 0.0..10.0 -> "%.1f".format(hours)
        else -> hours.toInt().toString()
    }


fun formatDurationShort(value: Double): String =
    when {
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
            when (val secondPart = seconds.roundToInt()) {
                0 -> "${minutePart}m"
                else -> "${minutePart}m ${secondPart}s"
            }
        }

        else -> {
            val hours = value / 3600
            val hourPart = hours.toLong()
            val minutes = (hours - hourPart) * 60
            val minutePart = minutes.roundToInt()
            val seconds = (minutes - minutePart) * 60
            val secondPart = seconds.roundToInt()
            when {
                minutePart == 0 && secondPart == 0 -> "${hourPart}h"
                secondPart == 0 -> "${hourPart}h ${minutePart}m"
                else -> "${hourPart}h ${minutePart}m ${secondPart}s"
            }
        }
    }

fun formatDuration(value: Double): String =
    when {
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
    return if (intervals.size <= 1) 0.0 else accumulateTripTime(
        intervals.sliceArray(
            IntRange(
                0,
                intervals.size - 2
            )
        )
    ) + accumulateTripPauses(intervals)
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
    currentTimeMillis: Long = SystemUtils.currentTimeMillis()
): Array<LongRange> = getTripIntervals(timeStates, null, currentTimeMillis)

fun getTripIntervals(
    timeStates: Array<TimeState>?,
    measurements: Array<HeartRateMeasurement>? = null,
): Array<LongRange> = getTripIntervals(
    timeStates,
    measurements?.firstOrNull()?.timestamp,
    measurements?.lastOrNull()?.timestamp
)

fun getTripIntervals(
    timeStates: Array<TimeState>?,
    measurements: Array<CadenceSpeedMeasurement>? = null,
): Array<LongRange> = getTripIntervals(
    timeStates,
    measurements?.firstOrNull()?.timestamp,
    measurements?.lastOrNull()?.timestamp
)

fun getTripIntervals(
    timeStates: Array<TimeState>?,
    measurements: Array<Measurements>? = null,
): Array<LongRange> = getTripIntervals(
    timeStates,
    measurements?.firstOrNull()?.time,
    measurements?.lastOrNull()?.time
)

fun getTripIntervals(
    timeStates: Array<TimeState>?,
    firstMeasurementTime: Long? = null,
    lastMeasurementTime: Long? = null,
): Array<LongRange> {
    val intervals = ArrayList<LongRange>()
    var intervalStart = -1L
    timeStates?.forEach { timeState ->
        if (isTripInProgress(timeState.state) && intervalStart < 0) intervalStart =
            timeState.timestamp
        if (intervalStart >= 0 && !isTripInProgress(timeState.state)) {
            intervals.add((intervalStart until timeState.timestamp))
            intervalStart = -1L
        }
    }
    if (!timeStates.isNullOrEmpty() && isTripInProgress(timeStates.last().state) && lastMeasurementTime != null) {
        if (timeStates.last().timestamp < lastMeasurementTime) {
            intervals.add((intervalStart..lastMeasurementTime))
        }
    }
    return if (intervals.isEmpty() && firstMeasurementTime != null && lastMeasurementTime != null) {
        arrayOf(firstMeasurementTime..lastMeasurementTime)
    } else intervals.toTypedArray()
}

fun getTripIntervals(
    timeStates: Array<TimeState>?,
): Array<LongRange> {
    val intervals = ArrayList<LongRange>()
    var intervalStart = -1L
    timeStates?.forEach { timeState ->
        if (isTripInProgress(timeState.state) && intervalStart < 0) intervalStart =
            timeState.timestamp
        if (intervalStart >= 0 && !isTripInProgress(timeState.state)) {
            intervals.add((intervalStart until timeState.timestamp))
            intervalStart = -1L
        }
    }
    return intervals.toTypedArray()
}

fun getStartTime(timeStates: Array<TimeState>) =
    timeStates.find { isTripInProgress(it.state) }?.timestamp

fun getEndTime(timeStates: Array<TimeState>) =
    timeStates.findLast { !isTripInProgress(it.state) }?.timestamp

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
    measurements: Array<HeartRateMeasurement>,
    intervals: Array<LongRange>,
): Array<Array<HeartRateMeasurement>> {
    val legs = ArrayList<Array<HeartRateMeasurement>>()
    intervals.forEach { interval ->
        legs.add(measurements.filter { interval.contains(it.timestamp) }.toTypedArray())
    }
    return legs.toTypedArray()
}

fun getTripLegs(
    measurements: Array<CadenceSpeedMeasurement>,
    intervals: Array<LongRange>,
): Array<Array<CadenceSpeedMeasurement>> {
    val legs = ArrayList<Array<CadenceSpeedMeasurement>>()
    intervals.forEach { interval ->
        legs.add(measurements.filter { interval.contains(it.timestamp) }.toTypedArray())
    }
    return legs.toTypedArray()
}

fun accumulateRevolutions(measurements: Array<CadenceSpeedMeasurement>): Long {
    var lastMeasurement: CadenceSpeedMeasurement? = null
    var totalRevolutions = 0L;
    measurements.forEach { measurement ->
        lastMeasurement
            ?.let { last ->
                if (validateSpeed(measurement, last)) {
                    totalRevolutions += measurement.revolutions - last.revolutions
                }
            }
        lastMeasurement = measurement
    }
    return totalRevolutions
}

fun getEffectiveCircumference(trip: Trip, measurements: Array<CadenceSpeedMeasurement>) =
    trip.distance?.div(accumulateRevolutions(measurements))?.toFloat()

suspend fun plotPath(
    measurements: Array<Measurements>,
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
            while (it.time > (nextTimeState()?.timestamp ?: Long.MAX_VALUE)) {
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
                    Location.distanceBetween(
                        lastLat,
                        lastLng,
                        it.latitude,
                        it.longitude,
                        distanceArray
                    )
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
            Log.d(
                "PLOT_PATH",
                String.format("Bounds could not be calculated: path size = %d", measurements.size)
            )
        }
        MapPath(paths.toTypedArray(), bounds)
    }

const val METERS_TO_FEET = 3.28084
const val FEET_TO_METERS = 1 / METERS_TO_FEET
const val METERS_TO_KM = 0.001
const val METERS_TO_MM = 1000.0
const val FEET_TO_MILES = 1.0 / 5280
const val SECONDS_TO_HOURS = 1.0 / 3600
const val INCHES_TO_FEET = 1 / 12.0
const val FEET_TO_INCHES = 12.0
const val KELVIN_TO_CELSIUS = 273.15

fun kelvinToCelsius(kelvin: Double) = kelvin - KELVIN_TO_CELSIUS
fun kelvinToFahrenheit(kelvin: Double) = kelvinToCelsius(kelvin) * 9.0 / 5.0 + 32.0

fun getUserTemperature(context: Context, temperature: Double): Int = getUserTemperature(
    getSystemOfMeasurement(context), temperature
)

fun getUserTemperature(systemOfMeasurement: String?, temperature: Double): Int =
    when (systemOfMeasurement) {
        "1" -> kelvinToFahrenheit(temperature)
        "2" -> kelvinToCelsius(temperature)
        else -> 1.0
    }.roundToInt()

fun getSystemSpeed(context: Context, speed: Double): Float =
    getSystemSpeed(context, speed.toFloat())

fun getSystemSpeed(systemOfMeasurement: String?, speed: Double): Float =
    getSystemSpeed(systemOfMeasurement, speed.toFloat())

fun getSystemSpeed(context: Context, speed: Int): Int =
    getSystemSpeed(getSystemOfMeasurement(context), speed.toFloat()).roundToInt()

fun getSystemSpeed(context: Context, speed: Float): Float =
    getSystemSpeed(getSystemOfMeasurement(context), speed)

fun getSystemSpeed(systemOfMeasurement: String?, speed: Float): Float =
    (when (systemOfMeasurement) {
        "1" -> 1 / (METERS_TO_FEET * FEET_TO_MILES / SECONDS_TO_HOURS)
        "2" -> 1 / (METERS_TO_KM / SECONDS_TO_HOURS)
        else -> 1.0
    } * speed).toFloat()

fun getRpm(context: Context, speed: Int, defaultCircumference: Float? = null): Int =
    getRpm(context, speed.toDouble(), defaultCircumference).toInt()

fun getRpm(context: Context, speed: Float, defaultCircumference: Float? = null): Float =
    getRpm(context, speed.toDouble(), defaultCircumference).toFloat()

fun getRpm(context: Context, speed: Double, defaultCircumference: Float? = null): Double =
    (getUserCircumferenceOrNull(context)
        ?: defaultCircumference)?.let { circ -> speed * 60f / circ }
        ?: 0.0

fun getUserSpeed(context: Context, meters: Double, seconds: Double): Float =
    getUserSpeed(context, meters / seconds)

fun getUserSpeed(context: Context, speed: Double): Float =
    getUserSpeed(context, speed.toFloat())

fun getUserSpeed(systemOfMeasurement: String?, speed: Double): Float =
    getUserSpeed(systemOfMeasurement, speed.toFloat())

fun getUserSpeed(context: Context, speed: Float): Float =
    getUserSpeed(getSystemOfMeasurement(context), speed)

fun getUserSpeed(systemOfMeasurement: String?, speed: Float): Float =
    (when (systemOfMeasurement) {
        "1" -> METERS_TO_FEET * FEET_TO_MILES / SECONDS_TO_HOURS
        "2" -> METERS_TO_KM / SECONDS_TO_HOURS
        else -> 1.0
    } * speed).toFloat()

fun getUserDistance(context: Context, meters: Double): Double = getUserDistance(
    getSystemOfMeasurement(context), meters
)

fun getUserDistance(systemOfMeasurement: String?, meters: Double): Double =
    when (systemOfMeasurement) {
        "1" -> METERS_TO_FEET * FEET_TO_MILES
        "2" -> METERS_TO_KM
        else -> 1.0
    } * meters

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
        Log.e("TRIP_UTILS_PREF", "metersToUserCircumference: Couldn't parse wheel circumference")
        String.format("%.3f", meters)
    }
}

fun userCircumferenceToMeters(input: Float?): Float? {
    return try {
        return when (val circumference =
            input ?: Float.NEGATIVE_INFINITY) {
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
        Log.e("TRIP_UTILS_PREF", "userCircumferenceToMeters: Couldn't parse wheel circumference")
        null
    }
}

fun userCircumferenceToMeters(input: String?): Float? =
    userCircumferenceToMeters(input?.takeIf { it != "" }?.toFloatOrNull())

fun getUserAltitude(context: Context, meters: Double): Double {
    val userConversionFactor =
        when (getSystemOfMeasurement(context)) {
            "1" -> METERS_TO_FEET
            else -> 1.0
        }
    return meters * userConversionFactor
}

fun getUserTemperatureUnit(context: Context) = getUserTemperatureUnit(
    getSystemOfMeasurement(context)
)

fun getUserTemperatureUnit(systemOfMeasurement: String?): String =
    when (systemOfMeasurement) {
        "1" -> "°F"
        "2" -> "°C"
        else -> "K"
    }

fun getUserDistanceUnitShort(context: Context): String = getUserDistanceUnitShort(
    getSystemOfMeasurement(context)
)

fun getUserDistanceUnitShort(systemOfMeasurement: String?): String =
    when (systemOfMeasurement) {
        "1" -> "mi"
        "2" -> "km"
        else -> "mi"
    }

fun getUserDistanceUnitLong(context: Context): String {
    return when (getSystemOfMeasurement(context)) {
        "1" -> "miles"
        "2" -> "kilometers"
        else -> "miles"
    }
}

fun getUserSpeedUnitShort(context: Context): String =
    when (getSystemOfMeasurement(context)) {
        "1" -> "mph"
        "2" -> "km/h"
        else -> "mph"
    }

fun getUserSpeedUnitShort(systemOfMeasurement: String?): String =
    when (systemOfMeasurement) {
        "1" -> "mph"
        "2" -> "km/h"
        else -> "mph"
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

fun incrementSplit(
    split: Split,
    durationDelta: Double,
    distanceDelta: Float,
): Split =
    split.copy(
        timestamp = SystemUtils.currentTimeMillis(),
        duration = split.duration + durationDelta,
        totalDuration = split.totalDuration + durationDelta,
        distance = split.distance + distanceDelta,
        totalDistance = split.totalDistance + distanceDelta,
    )

fun crossedSplitThreshold(
    unitConversionFactor: Double,
    newDistance: Double,
    oldDistance: Double,
): Boolean =
    newDistance > oldDistance && floor(newDistance * unitConversionFactor) > floor(oldDistance * unitConversionFactor)

fun crossedSplitThreshold(
    prefs: SharedPreferences,
    newDistance: Double,
    oldDistance: Double,
): Boolean =
    when (prefs.getString("display_units", "1")) {
        "1" -> METERS_TO_FEET * FEET_TO_MILES
        "2" -> METERS_TO_KM
        else -> 1.0
    }.let {
        crossedSplitThreshold(it, newDistance, oldDistance)
    }

fun crossedSplitThreshold(context: Context, newDistance: Double, oldDistance: Double): Boolean {
    return crossedSplitThreshold(
        getPreferences(context),
        newDistance,
        oldDistance
    )
}

fun calculateSplits(
    tripId: Long,
    measurements: Array<Measurements>,
    timeStates: Array<TimeState>?,
    sharedPreferences: SharedPreferences,
) = when (sharedPreferences.getString("display_units", "1")) {
    "1" -> METERS_TO_FEET * FEET_TO_MILES
    "2" -> METERS_TO_KM
    else -> 1.0
}.let {
    calculateSplits(tripId, measurements, timeStates, it)
}

fun calculateSplits(
    tripId: Long,
    measurements: Array<Measurements>,
    timeStates: Array<TimeState>?,
    unitConversionFactor: Double,
): ArrayList<Split> {
    val tripSplits = arrayListOf<Split>()
    var totalDistance = 0.0
    var totalActiveTime = 0.0
    var timestamp: Long = 0

    if (measurements.isEmpty()) return tripSplits

    val intervals = getTripIntervals(timeStates, measurements)
    if (intervals.isEmpty()) return tripSplits

    val legs = getTripLegs(measurements, intervals)
    if (legs.isEmpty()) return tripSplits

    legs.forEachIndexed { legIdx, leg ->
        if (leg.isEmpty()) return@forEachIndexed
        var prev = leg[0]
        for (measurementIdx in 1 until leg.size) {
            val lastSplit = getLastSplit(tripSplits)
            val curr = leg[measurementIdx]
            if (curr.hasAccuracy() && curr.accuracy > LOCATION_ACCURACY_THRESHOLD) continue;

            if (totalDistance == 0.0) {
                Log.v("calculateSplits", "first measurement:${prev.id}:${curr.id}")
            }
            totalDistance += getDistance(curr, prev)
            totalActiveTime =
                ((curr.time - (intervals[legIdx].first)) / 1e3) + accumulateTripTime(
                    intervals.sliceArray(
                        IntRange(0, legIdx - 1)
                    )
                )
            timestamp = curr.time

            if (crossedSplitThreshold(
                    unitConversionFactor,
                    totalDistance,
                    lastSplit.totalDistance
                )
            ) {
                tripSplits.add(
                    makeSplit(
                        tripId,
                        totalDistance,
                        totalActiveTime,
                        lastSplit,
                        timestamp
                    )
                )
            }
            prev = curr
        }
    }
    val lastSplit = getLastSplit(tripSplits)
    if (timestamp != lastSplit.timestamp) {
        tripSplits.add(
            makeSplit(
                tripId,
                totalDistance,
                totalActiveTime,
                lastSplit,
                timestamp
            )
        )
    }
    return tripSplits
}

fun getLastSplit(tripSplits: ArrayList<Split>) =
    if (tripSplits.isEmpty()) Split(
        0,
        0.0,
        0.0,
        0.0,
        0.0,
        0,
        0
    ) else tripSplits.last()

fun makeSplit(
    tripId: Long,
    totalDistance: Double,
    totalActiveTime: Double,
    lastSplit: Split,
    timestamp: Long,
): Split {
    return Split(
        timestamp = timestamp,
        duration = totalActiveTime - lastSplit.totalDuration,
        distance = totalDistance - lastSplit.totalDistance,
        totalDuration = totalActiveTime,
        totalDistance = totalDistance,
        tripId = tripId
    )
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
        if (it == 0f) 0f else it / getDifferenceRollover(
            adjustedTime,
            timeLast
        ) * 1024 * 60
    }
}

fun getRpm(rev: Int, revLast: Int, time: Int, timeLast: Int): Float {
    //NOTE: Does not handle 32-bit rollover, as the CSC spec states 32-bit values
    //do not rollover.
    return getDifferenceRollover(rev, revLast).toFloat().let {
        if (it == 0f) 0f else it / getDifferenceRollover(
            time,
            timeLast
        ) * 1024 * 60
    }
}

fun bearingToWindAngle(bearing: Float, windDirection: Int): Int =
    ((windDirection - bearing).toInt() + 540) % 360

fun bearingToIconRotation(bearing: Int, offset: Int = 0): Int =
    (bearing + offset) % 360

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

fun didSpeedDeviceFail(
    current: CadenceSpeedMeasurement,
    previous: CadenceSpeedMeasurement
): Boolean {
    //when device fails revolutions and lastEvent reset to zero
    return (current.lastEvent < previous.lastEvent && current.revolutions < previous.revolutions)
}

fun didCadenceDeviceFail(
    current: CadenceSpeedMeasurement,
    previous: CadenceSpeedMeasurement
): Boolean {
    val doubleRollover =
        (current.lastEvent < previous.lastEvent && current.revolutions < previous.revolutions)
    val prematureRollover =
        (previous.revolutions < 65525f && current.revolutions < previous.revolutions)
    val veryPrematureRollover =
        (previous.revolutions < 65400f && current.revolutions < previous.revolutions)
    val deviceReset = prematureRollover && doubleRollover

    return deviceReset || veryPrematureRollover
}

fun validateSpeed(current: CadenceSpeedMeasurement, previous: CadenceSpeedMeasurement): Boolean {
    val didNotUpdate = current.lastEvent == previous.lastEvent
    return !(didNotUpdate || didSpeedDeviceFail(current, previous))
}

fun validateCadence(current: CadenceSpeedMeasurement, previous: CadenceSpeedMeasurement): Boolean {
    val didNotUpdate = current.lastEvent == previous.lastEvent
    return !(didNotUpdate || didCadenceDeviceFail(current, previous))
}

fun getAverageCadenceTheHardWay(cadenceMeasurements: Array<CadenceSpeedMeasurement>): Float? {
    if (cadenceMeasurements.isNullOrEmpty()) return null

    var totalTime = 0L
    var totalRevs = 0
    var lastMeasurement: CadenceSpeedMeasurement? = null
    cadenceMeasurements.forEach { measurements ->
        lastMeasurement
            ?.let { last ->
                if (!didCadenceDeviceFail(measurements, last)) {
                    totalRevs += getDifferenceRollover(
                        measurements.revolutions,
                        last.revolutions
                    )
                    totalTime += getDifferenceRollover(
                        measurements.lastEvent,
                        last.lastEvent
                    )
                }
            }
        lastMeasurement = measurements
    }
    return totalRevs.toFloat() / totalTime * 1024f * 60f
}

fun getAverageCadenceTheEasyWay(cadenceMeasurements: Array<CadenceSpeedMeasurement>): Float {
    val totalRevs =
        getDifferenceRollover(
            cadenceMeasurements.last().revolutions,
            cadenceMeasurements.first().revolutions
        )
    val duration =
        (cadenceMeasurements.last().timestamp - cadenceMeasurements.first().timestamp) / 1000f / 60f

    return totalRevs.toFloat().div(duration)
}

fun getAverageCadenceFromRpm(measurements: Array<CadenceSpeedMeasurement>): Float? =
    try {
        measurements.mapNotNull { it.rpm }
            .average().toFloat()
    } catch (e: Exception) {
        null
    }

fun getAverageCadence(measurements: Array<CadenceSpeedMeasurement>): Float? =
    getAverageCadenceTheHardWay(measurements)

fun getAcceleration(
    durationDelta: Double,
    newSpeed: Float,
    oldSpeed: Float,
) = if (durationDelta == 0.0) 0f
else ((newSpeed - oldSpeed) / durationDelta).toFloat()

/*
fun getSpeed(
    new: Measurements,
    speedThreshold: Float,
    circumference: Float?,
): Float {
    return if (circumference != null && new.speedRpm != null) {
        val rps = new.speedRpm.div(60)
        circumference * rps
    } else if (new.speed > speedThreshold) new.speed else 0f
}
 */

fun getSpeed(
    new: Measurements,
    speedThreshold: Float,
): Float = if (new.speed > speedThreshold) new.speed else 0f

fun calculateSlope(
    derivedTripState: List<DerivedTripState>,
): Double =
    leastSquaresFitSlope(derivedTripState.map { Pair(it.totalDistance, it.altitude) })

fun calculateWheelCircumference(
    derivedTripState: Array<DerivedTripState>,
    sampleSize: Int,
    varianceThreshold: Double,
): Float? =
    derivedTripState.filter { it.circumference.isFinite() }.takeLast(sampleSize)
        .map { it.circumference }.let {
            if (it.size >= sampleSize && it.sampleVariance() < varianceThreshold) it.average()
                .toFloat() else null
        }

data class MapPath(val paths: Array<PolylineOptions>, val bounds: LatLngBounds?)

fun Array<Weather>.getAverageWind(): Pair<Double, Double> {
    var ew = 0.0
    var ns = 0.0
    this.forEach {
        ew += sin(it.windDirection * PI / 180) * it.windSpeed
        ns += cos(it.windDirection * PI / 180) * it.windSpeed
    }
    ew /= this.size * -1
    ns /= this.size * -1
    return Pair(
        sqrt(ew.pow(2) + ns.pow(2)),
        atan2(
            ew / this.size * -1,
            ns / this.size * -1
        ) * 180 / PI + 180
    )
}
