package com.kvl.cyclotrack

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Velocity
import com.kvl.cyclotrack.data.CadenceSpeedMeasurement
import com.kvl.cyclotrack.data.CyclingPowerMeasurement
import com.kvl.cyclotrack.data.HeartRateMeasurement
import com.kvl.cyclotrack.data.powerToCadence
import com.kvl.cyclotrack.util.SystemUtils
import com.kvl.cyclotrack.util.rideCalories
import com.kvl.cyclotrack.util.tripBiometrics
import com.kvl.cyclotrack.util.getSessionId
import com.kvl.cyclotrack.util.grantedHealthConnectPermissions
import com.kvl.cyclotrack.util.hasHealthConnectWriteAccess
import com.kvl.cyclotrack.util.healthConnectReadPermissions
import com.kvl.cyclotrack.util.isHealthConnectAvailable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.reflect.KClass

@Singleton
class HealthConnectApiService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val logTag = "HealthConnectApi"

    private val client: HealthConnectClient?
        get() = when (isHealthConnectAvailable(context)) {
            true -> HealthConnectClient.getOrCreate(context)
            false -> null
        }

    private val device = Device(type = Device.TYPE_PHONE)

    suspend fun canWriteWorkouts() = hasHealthConnectWriteAccess(context)

    suspend fun canReadBiometrics() =
        grantedHealthConnectPermissions(context).containsAll(healthConnectReadPermissions)

    // Health Connect keys records on (app, clientRecordId) and replaces one whose
    // clientRecordVersion is not newer, so insert is the update path too.
    suspend fun upsertSession(
        trip: Trip,
        timeStates: Array<TimeState>,
        measurements: Array<Measurements> = emptyArray(),
        heartRateMeasurements: Array<HeartRateMeasurement> = emptyArray(),
        speedMeasurements: Array<CadenceSpeedMeasurement> = emptyArray(),
        cadenceMeasurements: Array<CadenceSpeedMeasurement> = emptyArray(),
        powerMeasurements: Array<CyclingPowerMeasurement> = emptyArray(),
        wheelCircumference: Float = 0f,
    ): Boolean {
        val start = getStartTime(timeStates) ?: measurements.firstOrNull()?.time ?: trip.timestamp
        val end = getEndTime(timeStates) ?: measurements.lastOrNull()?.time
        ?: (trip.timestamp + ((trip.duration ?: 0.0) * 1000).toLong())

        return upsertSession(
            trip = trip,
            start = start,
            end = end,
            intervals = getTripIntervals(timeStates),
            measurements = measurements,
            heartRateMeasurements = heartRateMeasurements,
            speedMeasurements = speedMeasurements,
            cadenceMeasurements = cadenceMeasurements,
            powerMeasurements = powerMeasurements,
            wheelCircumference = wheelCircumference,
        )
    }

    suspend fun upsertSession(
        trip: Trip,
        start: Long,
        end: Long,
        intervals: Array<LongRange> = emptyArray(),
        measurements: Array<Measurements> = emptyArray(),
        heartRateMeasurements: Array<HeartRateMeasurement> = emptyArray(),
        speedMeasurements: Array<CadenceSpeedMeasurement> = emptyArray(),
        cadenceMeasurements: Array<CadenceSpeedMeasurement> = emptyArray(),
        powerMeasurements: Array<CyclingPowerMeasurement> = emptyArray(),
        wheelCircumference: Float = 0f,
    ): Boolean {
        val hc = client ?: return false
        val tripId = trip.id ?: return false
        val startInstant = Instant.ofEpochMilli(start)
        val endInstant = Instant.ofEpochMilli(end)
        if (!endInstant.isAfter(startInstant)) {
            Log.w(logTag, "Trip $tripId is not a positive interval; not syncing")
            return false
        }

        val sessionId = getSessionId(tripId)
        val offset = ZoneId.systemDefault().rules.getOffset(startInstant)
        // Any later write must win over what is already stored.
        val version = SystemUtils.currentTimeMillis()
        val route = buildRoute(measurements, start, end)

        // The one clear left in the write path, and it can only ever touch this ride's
        // own two calorie records: a ride that loses height between syncs writes just
        // one of the pair, and clientRecordId replaces only within its own type, so
        // the counterpart would otherwise stand stale beside it.
        val caloriesIds =
            listOf(caloriesRecordId(sessionId, true), caloriesRecordId(sessionId, false))
        calorieRecordTypes.forEach { type ->
            try {
                hc.deleteRecords(
                    recordType = type,
                    recordIdsList = emptyList(),
                    clientRecordIdsList = caloriesIds,
                )
            } catch (e: Exception) {
                Log.e(logTag, "Failed to clear $type for trip $tripId", e)
            }
        }

        hc.insertRecords(
            listOfNotNull<Record>(
                ExerciseSessionRecord(
                    startTime = startInstant,
                    startZoneOffset = offset,
                    endTime = endInstant,
                    endZoneOffset = offset,
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                    title = trip.name,
                    notes = trip.notes,
                    segments = intervals.filter { it.last > it.first }.map {
                        ExerciseSegment(
                            startTime = Instant.ofEpochMilli(it.first),
                            endTime = Instant.ofEpochMilli(it.last),
                            segmentType = ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING,
                        )
                    },
                    exerciseRoute = route,
                    metadata = Metadata.activelyRecorded(
                        device = device,
                        clientRecordId = sessionId,
                        clientRecordVersion = version,
                    ),
                ),
                trip.distance?.takeIf { it > 0 }?.let { distance ->
                    DistanceRecord(
                        startTime = startInstant,
                        startZoneOffset = offset,
                        endTime = endInstant,
                        endZoneOffset = offset,
                        distance = Length.meters(distance),
                        metadata = Metadata.activelyRecorded(
                            device = device,
                            clientRecordId = distanceRecordId(sessionId),
                            clientRecordVersion = version,
                        ),
                    )
                },
            ) + caloriesRecords(
                trip = trip,
                heartRateMeasurements = heartRateMeasurements,
                powerMeasurements = powerMeasurements,
                sessionId = sessionId,
                start = startInstant,
                end = endInstant,
                offset = offset,
                version = version,
            )
        )

        writeStreams(
            hc = hc,
            sessionId = sessionId,
            offset = offset,
            measurements = measurements,
            heartRateMeasurements = heartRateMeasurements,
            speedMeasurements = speedMeasurements,
            cadenceMeasurements = cadenceMeasurements,
            powerMeasurements = powerMeasurements,
            wheelCircumference = wheelCircumference,
        )
        Log.i(
            logTag,
            "Synced trip $tripId: ${route?.route?.size ?: 0} route points, " +
                    "${heartRateMeasurements.size} hr, ${powerMeasurements.size} power, " +
                    "${speedMeasurements.size} speed"
        )
        return true
    }

    // Health Connect keeps active and total energy apart, and a ride can support one
    // or both depending on whether its biometrics allow resting metabolism to be
    // separated out.
    private fun caloriesRecords(
        trip: Trip,
        heartRateMeasurements: Array<HeartRateMeasurement>,
        powerMeasurements: Array<CyclingPowerMeasurement>,
        sessionId: String,
        start: Instant,
        end: Instant,
        offset: ZoneOffset,
        version: Long,
    ): List<Record> {
        val biometrics = tripBiometrics(context, trip)

        // The estimators assert their way through missing weight, duration and
        // distance, so a ride lacking any of them simply reports no energy.
        val calories = runCatching {
            rideCalories(
                context = context,
                biometrics = biometrics,
                overview = trip,
                heartRate = getAverageHeartRate(heartRateMeasurements),
                powerMeasurements = powerMeasurements,
            )
        }.getOrElse {
            Log.d(logTag, "Trip ${trip.id} has no calorie estimate: ${it.message}")
            return emptyList()
        }
        Log.d(logTag, "Trip ${trip.id} calories: $calories")

        fun metadata(active: Boolean) = Metadata.activelyRecorded(
            device = device,
            clientRecordId = caloriesRecordId(sessionId, active),
            clientRecordVersion = version,
        )

        return listOfNotNull<Record>(
            calories.active?.takeIf { it > 0 }?.let {
                ActiveCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = offset,
                    endTime = end,
                    endZoneOffset = offset,
                    energy = Energy.kilocalories(it.toDouble()),
                    metadata = metadata(active = true),
                )
            },
            calories.total?.takeIf { it > 0 }?.let {
                TotalCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = offset,
                    endTime = end,
                    endZoneOffset = offset,
                    energy = Energy.kilocalories(it.toDouble()),
                    metadata = metadata(active = false),
                )
            },
        )
    }

    private suspend fun writeStreams(
        hc: HealthConnectClient,
        sessionId: String,
        offset: ZoneOffset,
        measurements: Array<Measurements>,
        heartRateMeasurements: Array<HeartRateMeasurement>,
        speedMeasurements: Array<CadenceSpeedMeasurement>,
        cadenceMeasurements: Array<CadenceSpeedMeasurement>,
        powerMeasurements: Array<CyclingPowerMeasurement>,
        wheelCircumference: Float,
    ) {
        heartRateMeasurements.asIterable()
            .toSamples("heart rate", { it.id }) {
                HeartRateRecord.Sample(
                    Instant.ofEpochMilli(it.timestamp),
                    it.heartRate.toLong()
                )
            }
            .writeSeries(hc, sessionId, "hr", { it.time }) { start, end, samples, metadata ->
                HeartRateRecord(
                    startTime = start,
                    startZoneOffset = offset,
                    endTime = end,
                    endZoneOffset = offset,
                    samples = samples,
                    metadata = metadata,
                )
            }

        powerMeasurements.asIterable()
            .toSamples("power", { it.id }) {
                PowerRecord.Sample(
                    Instant.ofEpochMilli(it.timestamp),
                    Power.watts(it.instantaneousPower.toDouble())
                )
            }
            .writeSeries(hc, sessionId, "power", { it.time }) { start, end, samples, metadata ->
                PowerRecord(
                    startTime = start,
                    startZoneOffset = offset,
                    endTime = end,
                    endZoneOffset = offset,
                    samples = samples,
                    metadata = metadata,
                )
            }

        // A power meter reports crank revolutions too, so a bike without a dedicated
        // cadence sensor still has cadence to publish. Which source a ride uses is
        // fixed by what it recorded, so the row ids stay stable across syncs.
        (cadenceMeasurements.takeIf { it.isNotEmpty() } ?: powerToCadence(powerMeasurements))
            .toList().rpmSeries()
            .toSamples("cadence", { it.sourceId }) {
                CyclingPedalingCadenceRecord.Sample(it.time, it.rpm.toDouble())
            }
            .writeSeries(hc, sessionId, "cadence", { it.time }) { start, end, samples, metadata ->
                CyclingPedalingCadenceRecord(
                    startTime = start,
                    startZoneOffset = offset,
                    endTime = end,
                    endZoneOffset = offset,
                    samples = samples,
                    metadata = metadata,
                )
            }

        speedSamples(speedMeasurements, measurements, wheelCircumference)
            .writeSeries(hc, sessionId, "speed", { it.time }) { start, end, samples, metadata ->
                SpeedRecord(
                    startTime = start,
                    startZoneOffset = offset,
                    endTime = end,
                    endZoneOffset = offset,
                    samples = samples,
                    metadata = metadata,
                )
            }
    }

    // Health Connect range-checks every sample in its constructor and throws on the
    // first one outside its bounds — a single glitched sensor reading would otherwise
    // cost the whole ride. Rather than hardcode limits that belong to the library,
    // build each sample and drop the ones it refuses.
    private fun <M, S> Iterable<M>.toSamples(
        stream: String,
        sourceId: (M) -> Long?,
        sample: (M) -> S,
    ): List<Sourced<S>> {
        val samples = ArrayList<Sourced<S>>()
        var rejected = 0
        forEach { measurement ->
            try {
                samples.add(Sourced(sourceId(measurement), sample(measurement)))
            } catch (e: IllegalArgumentException) {
                rejected++
            }
        }
        if (rejected > 0) {
            Log.w(logTag, "Health Connect rejected $rejected of $stream; dropped them")
        }
        return samples
    }

    // A wheel sensor beats GPS on speed accuracy, so it wins when the ride has one;
    // otherwise the location fixes still carry a usable speed.
    private fun speedSamples(
        speedMeasurements: Array<CadenceSpeedMeasurement>,
        measurements: Array<Measurements>,
        wheelCircumference: Float,
    ): List<Sourced<SpeedRecord.Sample>> =
        when {
            speedMeasurements.isNotEmpty() && wheelCircumference > 0f ->
                speedMeasurements.toList().rpmSeries()
                    .toSamples("wheel speed", { it.sourceId }) {
                        SpeedRecord.Sample(
                            it.time,
                            Velocity.metersPerSecond(
                                (it.rpm / 60 * wheelCircumference).toDouble()
                            )
                        )
                    }

            else -> measurements.filter { it.speed.isFinite() && it.speed >= 0f }
                .toSamples("gps speed", { it.id }) {
                    SpeedRecord.Sample(
                        Instant.ofEpochMilli(it.time),
                        Velocity.metersPerSecond(it.speed.toDouble())
                    )
                }
        }

    private fun buildRoute(
        measurements: Array<Measurements>,
        start: Long,
        end: Long,
    ): ExerciseRoute? =
        measurements
            .filter { it.time in start..end && (it.latitude != 0.0 || it.longitude != 0.0) }
            .let { points ->
                // ponytail: Health Connect does not document a route point ceiling and
                // rejects the whole session if it has one. Thin evenly rather than risk
                // it; 10k points is ~3h at 1Hz.
                val stride = ceil(points.size.toDouble() / MAX_ROUTE_POINTS).toInt().coerceAtLeast(1)
                points.filterIndexed { index, _ -> index % stride == 0 }
            }
            .map {
                ExerciseRoute.Location(
                    time = Instant.ofEpochMilli(it.time),
                    latitude = it.latitude,
                    longitude = it.longitude,
                    horizontalAccuracy = Length.meters(it.accuracy.toDouble()),
                    altitude = Length.meters(it.altitude),
                )
            }
            .takeIf { it.isNotEmpty() }
            ?.let { ExerciseRoute(it) }

    // Removing a ride is the one place deletion is the point rather than a way to
    // make room, so it is the only place deletes remain. The streams are keyed on
    // measurement row ids, and recomputing those would mean reloading every sample
    // just to throw them away — the ride's own window is enough, and rides do not
    // overlap.
    suspend fun deleteTrip(tripId: Long, start: Long, end: Long) {
        val hc = client ?: return
        val sessionId = getSessionId(tripId)

        mapOf(
            ExerciseSessionRecord::class to listOf(sessionId),
            DistanceRecord::class to listOf(distanceRecordId(sessionId)),
            ActiveCaloriesBurnedRecord::class to listOf(caloriesRecordId(sessionId, true)),
            TotalCaloriesBurnedRecord::class to listOf(caloriesRecordId(sessionId, false)),
        ).forEach { (type, ids) ->
            try {
                hc.deleteRecords(
                    recordType = type,
                    recordIdsList = emptyList(),
                    clientRecordIdsList = ids,
                )
            } catch (e: Exception) {
                Log.e(logTag, "Failed to remove $ids from Health Connect", e)
            }
        }

        if (end > start) {
            val window = TimeRangeFilter.between(
                Instant.ofEpochMilli(start), Instant.ofEpochMilli(end)
            )
            streamRecordTypes.forEach { type ->
                try {
                    hc.deleteRecords(type, window)
                } catch (e: Exception) {
                    Log.e(logTag, "Failed to remove $type for trip $tripId", e)
                }
            }
        }
        Log.d(logTag, "Removed trip $tripId from Health Connect")
    }

    suspend fun deleteAllData() {
        val hc = client ?: return
        val everything = TimeRangeFilter.after(Instant.EPOCH)
        (streamRecordTypes + listOf(ExerciseSessionRecord::class, DistanceRecord::class))
            .forEach { type ->
                try {
                    hc.deleteRecords(type, everything)
                } catch (e: Exception) {
                    Log.e(logTag, "Failed to remove all $type from Health Connect", e)
                }
            }
    }

    private suspend fun <T : Record> readLatest(
        type: KClass<T>,
        timestamp: Long,
    ): T? = try {
        client?.readRecords(
            ReadRecordsRequest(
                recordType = type,
                timeRangeFilter = TimeRangeFilter.before(Instant.ofEpochMilli(timestamp)),
                ascendingOrder = false,
                pageSize = 1,
            )
        )?.records?.firstOrNull()
    } catch (e: Exception) {
        Log.e(logTag, "Failed to read $type from Health Connect", e)
        null
    }

    suspend fun getLatestHeight(timestamp: Long = SystemUtils.currentTimeMillis()): Float? =
        readLatest(HeightRecord::class, timestamp)?.height?.inMeters?.toFloat()

    suspend fun getLatestWeight(timestamp: Long = SystemUtils.currentTimeMillis()): Float? =
        readLatest(WeightRecord::class, timestamp)?.weight?.inKilograms?.toFloat()

    suspend fun getLatestRestingHeartRate(timestamp: Long = SystemUtils.currentTimeMillis()): Int? =
        readLatest(RestingHeartRateRecord::class, timestamp)?.beatsPerMinute?.toInt()

    // Chunked so a long ride does not hand Health Connect one enormous series record.
    //
    // Each chunk is keyed on the database row its first sample came from, which makes
    // the id deterministic: re-syncing a ride produces the same ids and Health Connect
    // replaces the records in place. Nothing is deleted to make room, so a sync can no
    // longer remove data it then fails to write back.
    //
    // ponytail: chunk boundaries hold because measurements are only ever appended to a
    // ride. A ride whose stream switched source — GPS speed to wheel speed after a
    // circumference edit — would write under new ids and leave the old set behind.
    private suspend fun <S> List<Sourced<S>>.writeSeries(
        hc: HealthConnectClient,
        sessionId: String,
        stream: String,
        sampleTime: (S) -> Instant,
        record: (Instant, Instant, List<S>, Metadata) -> Record,
    ) {
        if (isEmpty()) return
        val version = SystemUtils.currentTimeMillis()

        chunked(SAMPLES_PER_RECORD).mapNotNull { chunk ->
            val samples = chunk.map { it.sample }
            val start = sampleTime(samples.first())
            // An interval record must end strictly after it starts, which a
            // single-sample chunk would otherwise violate.
            val end = maxOf(sampleTime(samples.last()), start.plusMillis(1))
            chunk.first().sourceId?.let { rowId ->
                record(
                    start, end, samples,
                    Metadata.activelyRecorded(
                        device = device,
                        clientRecordId = "$sessionId-$stream-$rowId",
                        clientRecordVersion = version,
                    )
                )
            }
        }.chunked(RECORDS_PER_INSERT).forEach { hc.insertRecords(it) }
    }

    companion object {
        private const val SAMPLES_PER_RECORD = 1000
        private const val RECORDS_PER_INSERT = 100
        private const val MAX_ROUTE_POINTS = 10_000

        private val calorieRecordTypes = listOf(
            ActiveCaloriesBurnedRecord::class,
            TotalCaloriesBurnedRecord::class,
        )

        private val streamRecordTypes = listOf(
            HeartRateRecord::class,
            PowerRecord::class,
            CyclingPedalingCadenceRecord::class,
            SpeedRecord::class,
        )
    }
}

private fun distanceRecordId(sessionId: String) = "$sessionId-distance"

private fun caloriesRecordId(sessionId: String, active: Boolean) =
    "$sessionId-${if (active) "active" else "total"}-calories"

// The recorded rpm is what the sensor service computed live, and the FIT export
// reads it directly for the same reason: crank revolutions are optional in the BLE
// cycling power characteristic, and powerToCadence defaults a missing counter to
// zero — deriving from that yields a stream of zeros on a meter that omits it.
//
// Only when nothing recorded an rpm does the cumulative counter get differenced,
// which needs the previous reading, so that first measurement only seeds it.
internal fun List<CadenceSpeedMeasurement>.rpmSeries(): List<RpmSample> {
    val series = ArrayList<RpmSample>(size)
    var previous: CadenceSpeedMeasurement? = null
    forEach { current ->
        (current.rpm ?: previous?.takeIf { it.rpm != 0f }?.let { last ->
            getRpm(
                rev = current.revolutions,
                revLast = last.revolutions,
                time = current.lastEvent,
                timeLast = last.lastEvent,
            )
        })?.takeIf { it.isFinite() && it >= 0f }?.let {
            series.add(RpmSample(current.id, Instant.ofEpochMilli(current.timestamp), it))
        }
        previous = current
    }
    return series
}

// A sample paired with the database row it came from, so the record written from it
// can be keyed on something stable across syncs.
internal data class Sourced<S>(val sourceId: Long?, val sample: S)

internal data class RpmSample(val sourceId: Long?, val time: Instant, val rpm: Float)
