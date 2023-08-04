package com.kvl.cyclotrack

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class CalculateSplitsTest {
    private lateinit var db: SQLiteDatabase

    @Before
    fun initializeDb() {
        db = getDb()
    }

    @After
    fun releaseDb() {
        db.close()
    }

    private fun makeTestMeasurements(i: Long) = Measurements(
        id = i,
        tripId = 1,
        time = i * 1000L,
        accuracy = 1f,
        bearing = 0f,
        altitude = 0.0,
        latitude = 0.0,
        longitude = i * 0.00001 / 1.113195,
        speed = 1f,
        elapsedRealtimeNanos = i * 1000L,
    )

    private fun getMeasurementsArray(length: Long): Array<Measurements> {
        val testMeasurements = ArrayList<Measurements>()
        for (i in 0..length) {
            testMeasurements.add(
                makeTestMeasurements(i)
            )
        }
        return testMeasurements.toTypedArray()
    }

    private fun getDb(): SQLiteDatabase {
        getInstrumentation().targetContext.applicationContext.let { appContext ->
            appContext?.resources?.openRawResource(
                R.raw.test_db_20230802
            )?.let {
                it.copyTo(FileOutputStream("/data/data/${appContext.packageName}/databases/test-db"))
            }
        }
        val context = getInstrumentation().targetContext
        val dbPath = context.getDatabasePath("test-db")
        return SQLiteDatabase.openDatabase(dbPath.toString(), null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun getMeasurements(tripId: Long): Array<Measurements> {
        val measurements = ArrayList<Measurements>()
        val measurementsCursor =
            db.rawQuery(
                "SELECT * from Measurements WHERE tripId = $tripId ORDER BY time ASC",
                emptyArray(),
                null
            )
        measurementsCursor.moveToFirst()
        while (!measurementsCursor.isAfterLast) {
            measurements.add(
                Measurements(
                    tripId = measurementsCursor.getLong(measurementsCursor.getColumnIndex("tripId")),
                    accuracy = measurementsCursor.getFloat(measurementsCursor.getColumnIndex("accuracy")),
                    altitude = measurementsCursor.getDouble(measurementsCursor.getColumnIndex("altitude")),
                    bearing = measurementsCursor.getFloat(measurementsCursor.getColumnIndex("bearing")),
                    elapsedRealtimeNanos = measurementsCursor.getLong(
                        measurementsCursor.getColumnIndex(
                            "elapsedRealtimeNanos"
                        )
                    ),
                    latitude = measurementsCursor.getDouble(measurementsCursor.getColumnIndex("latitude")),
                    longitude = measurementsCursor.getDouble(measurementsCursor.getColumnIndex("longitude")),
                    speed = measurementsCursor.getFloat(measurementsCursor.getColumnIndex("speed")),
                    time = measurementsCursor.getLong(measurementsCursor.getColumnIndex("time")),
                    id = measurementsCursor.getLong(measurementsCursor.getColumnIndex("id")),
                )
            )
            measurementsCursor.moveToNext()
        }

        return measurements.toTypedArray()
    }

    private fun getTimeStates(tripId: Long): Array<TimeState> {
        val timeStates = ArrayList<TimeState>()
        val timeStatesCursor =
            db.rawQuery(
                "SELECT * from TimeState WHERE tripId = $tripId ORDER BY timestamp asc",
                emptyArray(),
                null
            )
        timeStatesCursor.moveToFirst()
        while (!timeStatesCursor.isAfterLast) {
            timeStates.add(
                TimeState(
                    tripId = timeStatesCursor.getLong(timeStatesCursor.getColumnIndex("tripId")),
                    state = TimeStateEnumConverter().toTimeStateEnum(
                        timeStatesCursor.getInt(
                            timeStatesCursor.getColumnIndex("state")
                        )
                    ),
                    timestamp = timeStatesCursor.getLong(timeStatesCursor.getColumnIndex("timestamp")),
                    auto = timeStatesCursor.getInt(timeStatesCursor.getColumnIndex("auto")) != 0,
                    id = timeStatesCursor.getLongOrNull(timeStatesCursor.getColumnIndex("id")),
                    originalTripId = timeStatesCursor.getLongOrNull(
                        timeStatesCursor.getColumnIndex(
                            "originalTripId"
                        )
                    ),
                )
            )
            timeStatesCursor.moveToNext()
        }
        return timeStates.toTypedArray()
    }

    private fun getTripIds(): Array<Long> {
        val tripIds = ArrayList<Long>()
        val cursor =
            db.rawQuery(
                "SELECT id from Trip",
                emptyArray(),
                null
            )
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            tripIds.add(cursor.getLong(cursor.getColumnIndex("id")))
            cursor.moveToNext()
        }
        return tripIds.toTypedArray()
    }

    private fun testAlgorithm(
        measurements: Array<Measurements>,
        newTripSplits: ArrayList<Split>,
        testTripId: Long,
        timeStates: Array<TimeState>,
    ) {
        var old = measurements[0]
        for (i in 1 until measurements.size) {
            val new = measurements[i]

            // CAUSES MOST TESTS TO FAIL
            if (new.hasAccuracy() && new.accuracy > LOCATION_ACCURACY_THRESHOLD) continue;

            val (secondLast, lastSplit) = newTripSplits.toTypedArray().lastTwo(testTripId)
            timeStates.findLast { it.timestamp <= new.time }?.let { currentTimeState ->
                val intervals =
                    getTripInProgressIntervals(
                        timeStates.slice(IntRange(0, timeStates.indexOf(currentTimeState)))
                            .toTypedArray(), new.time + 1
                    )
                if ((currentTimeState.state == TimeStateEnum.RESUME || currentTimeState.state == TimeStateEnum.START) &&
                    intervals.isNotEmpty() && intervals.last()
                        .contains(new.time) && intervals.last().contains(old.time)
                ) {
                    val distanceDelta = getDistance(old, new)
                    val totalDuration = accumulateTripTime(
                        intervals
                    )

                    val durationDelta = totalDuration - lastSplit.totalDuration

                    if (lastSplit.totalDistance == 0.0) {
                        Log.v("CalculateSplitsTest", "first measurement:${old.id}:${new.id}")
                    }

                    val split = doSplitStuff(
                        lastSplit,
                        durationDelta,
                        distanceDelta,
                        secondLast.totalDistance,
                        getUserDistance(getInstrumentation().targetContext, 1.0)
                    )
                    if (newTripSplits.isNotEmpty() && split.distance >= lastSplit.distance) {
                        newTripSplits.remove(newTripSplits.last())
                    }
                    newTripSplits.add(split)

                } else {
                    Log.v("CalculateSplitsTest", "Trip is paused")
                }
            }
            old = new;
        }
    }

    @Test
    fun calculateSplitsTripInProgress_compare_41() {
        val testTripId = 41L
        val measurements = getMeasurements(testTripId)
        val timeStates = getTimeStates(testTripId)
        val fakeTimeStates = arrayOf(
            TimeState(
                tripId = 41,
                state = TimeStateEnum.START,
                timestamp = measurements.first().time
            ),
            TimeState(
                tripId = 41,
                state = TimeStateEnum.STOP,
                timestamp = measurements.last().time + 1
            )
        )
        val splits = calculateSplits(
            tripId = testTripId,
            measurements = measurements,
            timeStates = timeStates,
            unitConversionFactor = METERS_TO_FEET * FEET_TO_MILES
        )
        assertThat("splits size", splits.size, greaterThan(0))

        val newTripSplits = ArrayList<Split>()
        testAlgorithm(measurements, newTripSplits, testTripId, fakeTimeStates)
        Assert.assertArrayEquals(
            splits.map { it.distance }.toDoubleArray(),
            newTripSplits.map { it.distance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDistance }.toDoubleArray(),
            newTripSplits.map { it.totalDistance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.duration }.toDoubleArray(),
            newTripSplits.map { it.duration }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDuration }.toDoubleArray(),
            newTripSplits.map { it.totalDuration }.toDoubleArray(),
            0.01
        )
    }

    @Test
    fun calculateSplitsTripInProgress_compare_309() {
        val testTripId = 1L
        val measurements = getMeasurements(309)
        val timeStates = getTimeStates(309)
        val splits = calculateSplits(
            tripId = testTripId,
            measurements = measurements,
            timeStates = timeStates,
            unitConversionFactor = METERS_TO_FEET * FEET_TO_MILES
        )
        assertThat("splits size", splits.size, greaterThan(0))

        val newTripSplits = ArrayList<Split>()
        testAlgorithm(measurements, newTripSplits, testTripId, timeStates)
        Assert.assertArrayEquals(
            splits.map { it.distance }.toDoubleArray(),
            newTripSplits.map { it.distance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDistance }.toDoubleArray(),
            newTripSplits.map { it.totalDistance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.duration }.toDoubleArray(),
            newTripSplits.map { it.duration }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDuration }.toDoubleArray(),
            newTripSplits.map { it.totalDuration }.toDoubleArray(),
            0.01
        )
    }

    @Test
    fun calculateSplitsTripInProgress_compare_597() {
        val testTripId = 1L
        val measurements = getMeasurements(597)
        val timeStates = getTimeStates(597)
        val splits = calculateSplits(
            tripId = testTripId,
            measurements = measurements,
            timeStates = timeStates,
            unitConversionFactor = METERS_TO_FEET * FEET_TO_MILES
        )
        assertThat("splits size", splits.size, greaterThan(0))

        val newTripSplits = ArrayList<Split>()
        testAlgorithm(measurements, newTripSplits, testTripId, timeStates)
        Assert.assertArrayEquals(
            splits.map { it.distance }.toDoubleArray(),
            newTripSplits.map { it.distance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDistance }.toDoubleArray(),
            newTripSplits.map { it.totalDistance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.duration }.toDoubleArray(),
            newTripSplits.map { it.duration }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDuration }.toDoubleArray(),
            newTripSplits.map { it.totalDuration }.toDoubleArray(),
            0.01
        )
    }

    @Test
    fun calculateSplitsTripInProgress_compare_238() {
        val testTripId = 1L
        val measurements = getMeasurements(238)
        val timeStates = getTimeStates(238)
        val splits = calculateSplits(
            tripId = testTripId,
            measurements = measurements,
            timeStates = timeStates,
            unitConversionFactor = METERS_TO_FEET * FEET_TO_MILES
        )
        assertThat("splits size", splits.size, greaterThan(0))

        val newTripSplits = ArrayList<Split>()
        testAlgorithm(
            measurements,
            newTripSplits,
            testTripId,
            timeStates
        )
        Assert.assertArrayEquals(
            splits.map { it.distance }.toDoubleArray(),
            newTripSplits.map { it.distance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDistance }.toDoubleArray(),
            newTripSplits.map { it.totalDistance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.duration }.toDoubleArray(),
            newTripSplits.map { it.duration }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDuration }.toDoubleArray(),
            newTripSplits.map { it.totalDuration }.toDoubleArray(),
            0.01
        )
    }

    @Test
    fun calculateSplitsTripInProgress_compare_265() {
        val testTripId = 265L
        val measurements = getMeasurements(testTripId)
        val timeStates = getTimeStates(testTripId)
        val splits = calculateSplits(
            tripId = testTripId,
            measurements = measurements,
            timeStates = timeStates,
            unitConversionFactor = METERS_TO_FEET * FEET_TO_MILES
        )

        val newTripSplits = ArrayList<Split>()
        testAlgorithm(
            measurements,
            newTripSplits,
            testTripId,
            timeStates
        )

        Log.v("CalculateTripSplits", "${newTripSplits.last()}")
        Assert.assertArrayEquals(
            splits.map { it.distance }.toDoubleArray(),
            newTripSplits.map { it.distance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDistance }.toDoubleArray(),
            newTripSplits.map { it.totalDistance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.duration }.toDoubleArray(),
            newTripSplits.map { it.duration }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDuration }.toDoubleArray(),
            newTripSplits.map { it.totalDuration }.toDoubleArray(),
            0.01
        )
    }

    @Test
    fun calculateSplitsTripInProgress_compare_556() {
        val testTripId = 556L
        val measurements = getMeasurements(testTripId)
        val timeStates = getTimeStates(testTripId)
        val splits = calculateSplits(
            tripId = testTripId,
            measurements = measurements,
            timeStates = timeStates,
            unitConversionFactor = METERS_TO_FEET * FEET_TO_MILES
        )

        val newTripSplits = ArrayList<Split>()
        testAlgorithm(
            measurements,
            newTripSplits,
            testTripId,
            timeStates
        )

        Log.v("CalculateTripSplits", "${newTripSplits.last()}")
        Assert.assertArrayEquals(
            splits.map { it.distance }.toDoubleArray(),
            newTripSplits.map { it.distance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDistance }.toDoubleArray(),
            newTripSplits.map { it.totalDistance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.duration }.toDoubleArray(),
            newTripSplits.map { it.duration }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDuration }.toDoubleArray(),
            newTripSplits.map { it.totalDuration }.toDoubleArray(),
            0.01
        )
    }

    @Test
    fun calculateSplitsTripInProgress_compare_392() {
        val testTripId = 392L
        val measurements = getMeasurements(testTripId)
        val timeStates = getTimeStates(testTripId)
        val splits = calculateSplits(
            tripId = testTripId,
            measurements = measurements,
            timeStates = timeStates,
            unitConversionFactor = METERS_TO_FEET * FEET_TO_MILES
        )

        val newTripSplits = ArrayList<Split>()
        testAlgorithm(
            measurements,
            newTripSplits,
            testTripId,
            timeStates
        )

        Assert.assertArrayEquals(
            splits.map { it.distance }.toDoubleArray(),
            newTripSplits.map { it.distance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDistance }.toDoubleArray(),
            newTripSplits.map { it.totalDistance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.duration }.toDoubleArray(),
            newTripSplits.map { it.duration }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDuration }.toDoubleArray(),
            newTripSplits.map { it.totalDuration }.toDoubleArray(),
            0.01
        )
    }

    @Test
    fun calculateSplitsTripInProgress_compare_489() {
        val testTripId = 1L
        val measurements = getMeasurements(489)
        val timeStates = getTimeStates(489)
        val splits = calculateSplits(
            tripId = testTripId,
            measurements = measurements,
            timeStates = timeStates,
            unitConversionFactor = METERS_TO_FEET * FEET_TO_MILES
        )
        assertThat("splits size", splits.size, greaterThan(0))

        val newTripSplits = ArrayList<Split>()
        testAlgorithm(
            measurements,
            newTripSplits,
            testTripId,
            timeStates
        )
        Assert.assertEquals(splits.size, newTripSplits.size)
        Assert.assertArrayEquals(
            splits.map { it.distance }.toDoubleArray(),
            newTripSplits.map { it.distance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDistance }.toDoubleArray(),
            newTripSplits.map { it.totalDistance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.duration }.toDoubleArray(),
            newTripSplits.map { it.duration }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDuration }.toDoubleArray(),
            newTripSplits.map { it.totalDuration }.toDoubleArray(),
            0.01
        )
    }

    @Test
    fun calculateSplitsTripInProgress_compare_612() {
        val testTripId = 1L
        val measurements = getMeasurements(612)
        val timeStates = getTimeStates(612)
        val splits = calculateSplits(
            tripId = testTripId,
            measurements = measurements,
            timeStates = timeStates,
            unitConversionFactor = METERS_TO_FEET * FEET_TO_MILES
        )
        assertThat("splits size", splits.size, greaterThan(0))

        val newTripSplits = ArrayList<Split>()
        testAlgorithm(
            measurements,
            newTripSplits,
            testTripId,
            timeStates
        )
        Assert.assertEquals(splits.size, newTripSplits.size)
        Assert.assertArrayEquals(
            splits.map { it.distance }.toDoubleArray(),
            newTripSplits.map { it.distance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDistance }.toDoubleArray(),
            newTripSplits.map { it.totalDistance }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.duration }.toDoubleArray(),
            newTripSplits.map { it.duration }.toDoubleArray(),
            0.01
        )
        Assert.assertArrayEquals(
            splits.map { it.totalDuration }.toDoubleArray(),
            newTripSplits.map { it.totalDuration }.toDoubleArray(),
            0.01
        )
    }

    @Test
    fun calculateSplitsTripInProgress_prepause() {
        val testTripId = 1L
        arrayOf(454L, 522L, 549L, 489L)
            .forEach { tripId ->
                Log.v("CalculateSplitsTest", "trip ID: $tripId")
                val measurements = getMeasurements(tripId)
                val timeStates = getTimeStates(tripId)
                val splits = calculateSplits(
                    tripId = testTripId,
                    measurements = measurements,
                    timeStates = timeStates,
                    unitConversionFactor = METERS_TO_FEET * FEET_TO_MILES
                )
                assertThat("splits size", splits.size, greaterThan(0))

                val newTripSplits = ArrayList<Split>()
                testAlgorithm(
                    measurements,
                    newTripSplits,
                    testTripId,
                    timeStates
                )
                Assert.assertEquals(splits.size, newTripSplits.size)
                Assert.assertArrayEquals(
                    splits.map { it.distance }.toDoubleArray(),
                    newTripSplits.map { it.distance }.toDoubleArray(),
                    0.01
                )
                Assert.assertArrayEquals(
                    splits.map { it.totalDistance }.toDoubleArray(),
                    newTripSplits.map { it.totalDistance }.toDoubleArray(),
                    0.01
                )
                Assert.assertArrayEquals(
                    splits.map { it.duration }.toDoubleArray(),
                    newTripSplits.map { it.duration }.toDoubleArray(),
                    0.01
                )
                Assert.assertArrayEquals(
                    splits.map { it.totalDuration }.toDoubleArray(),
                    newTripSplits.map { it.totalDuration }.toDoubleArray(),
                    0.01
                )
            }
    }

    @Test
    fun calculateSplitsTripInProgress_failures() {
        val testTripId = 1L
        //Skip 245: Bad data, no measurements, invalid time states
        //Skip 41: No time states
        arrayOf(238L, 183L, 454L, 489L, 522L, 549L, 392L, 265L, 556L)
            .forEach { tripId ->
                Log.v("CalculateSplitsTest", "trip ID: $tripId")
                val measurements = getMeasurements(tripId)
                val timeStates = getTimeStates(tripId)
                val splits = calculateSplits(
                    tripId = testTripId,
                    measurements = measurements,
                    timeStates = timeStates,
                    unitConversionFactor = METERS_TO_FEET * FEET_TO_MILES
                )
                assertThat("splits size", splits.size, greaterThan(0))

                val newTripSplits = ArrayList<Split>()
                testAlgorithm(measurements, newTripSplits, testTripId, timeStates)
                Assert.assertEquals(splits.size, newTripSplits.size)
                Assert.assertArrayEquals(
                    splits.map { it.distance }.toDoubleArray(),
                    newTripSplits.map { it.distance }.toDoubleArray(),
                    0.01
                )
                Assert.assertArrayEquals(
                    splits.map { it.totalDistance }.toDoubleArray(),
                    newTripSplits.map { it.totalDistance }.toDoubleArray(),
                    0.01
                )
                Assert.assertArrayEquals(
                    splits.map { it.duration }.toDoubleArray(),
                    newTripSplits.map { it.duration }.toDoubleArray(),
                    0.01
                )
                Assert.assertArrayEquals(
                    splits.map { it.totalDuration }.toDoubleArray(),
                    newTripSplits.map { it.totalDuration }.toDoubleArray(),
                    0.01
                )
            }
    }

    /* TAKES TOO LONG TO RUN EVERY TIME
    @Test
    fun calculateSplitsTripInProgress_compare_all_trips() {
        val testTripId = 1L
        val tripIds = getTripIds()
        tripIds
            .filter { !arrayOf(41L, 245L).contains(it) }
            .forEach { tripId ->
                Log.v("CalculateSplitsTest", "trip ID: $tripId")
                val measurements = getMeasurements(tripId)
                val timeStates = getTimeStates(tripId)
                val splits = calculateSplits(
                    tripId = testTripId,
                    measurements = measurements,
                    timeStates = timeStates,
                    unitConversionFactor = METERS_TO_FEET * FEET_TO_MILES
                )
                assertThat("splits size", splits.size, greaterThan(0))

                val newTripSplits = ArrayList<Split>()
                testAlgorithm(measurements, newTripSplits, testTripId, timeStates)
                Assert.assertEquals(splits.size, newTripSplits.size)
                Assert.assertArrayEquals(
                    splits.map { it.distance }.toDoubleArray(),
                    newTripSplits.map { it.distance }.toDoubleArray(),
                    0.01
                )
                Assert.assertArrayEquals(
                    splits.map { it.totalDistance }.toDoubleArray(),
                    newTripSplits.map { it.totalDistance }.toDoubleArray(),
                    0.01
                )
                Assert.assertArrayEquals(
                    splits.map { it.duration }.toDoubleArray(),
                    newTripSplits.map { it.duration }.toDoubleArray(),
                    0.01
                )
                Assert.assertArrayEquals(
                    splits.map { it.totalDuration }.toDoubleArray(),
                    newTripSplits.map { it.totalDuration }.toDoubleArray(),
                    0.01
                )
            }
    }*/

    @Test
    fun calculateSplits_no_pauses() {
        val testMeasurements = getMeasurementsArray(50000)
        val testTimeStates = arrayListOf(
            TimeState(
                tripId = 1,
                state = TimeStateEnum.START,
                timestamp = testMeasurements.first().time,
                false
            ),
            TimeState(
                tripId = 1,
                state = TimeStateEnum.STOP,
                timestamp = testMeasurements.last().time + 1,
                false
            ),
        )
        val splits = calculateSplits(
            1,
            testMeasurements,
            testTimeStates.toTypedArray(),
            METERS_TO_FEET * FEET_TO_MILES
        )
        Assert.assertEquals(32, splits.size)
        Assert.assertEquals(50000.0, splits.last().totalDistance, 0.01)
        Assert.assertEquals(50000.0, splits.last().totalDuration, 0.0)
    }

    @Test
    fun calculateSplits_pause_midway() {
        val testMeasurements = getMeasurementsArray(50000)
        val pauseAt = 25000
        val pauseTime = 30
        val testTimeStates = arrayListOf(
            TimeState(tripId = 1, state = TimeStateEnum.START, timestamp = 0L, false),
            TimeState(
                tripId = 1,
                state = TimeStateEnum.PAUSE,
                timestamp = testMeasurements[pauseAt].time + 1,
                false
            ),
            TimeState(
                tripId = 1,
                state = TimeStateEnum.RESUME,
                timestamp = testMeasurements[pauseAt + pauseTime].time,
                false
            ),
            TimeState(
                tripId = 1,
                state = TimeStateEnum.STOP,
                timestamp = testMeasurements.last().time + 1,
                false
            ),
        )
        val splits = calculateSplits(
            1,
            testMeasurements,
            testTimeStates.toTypedArray(),
            METERS_TO_FEET * FEET_TO_MILES
        )
        Assert.assertEquals(32, splits.size)
        Assert.assertEquals(50000.0 - pauseTime, splits.last().totalDistance, 0.01)
        Assert.assertEquals(50000.0 - pauseTime, splits.last().totalDuration, 0.0)
    }

    @Test
    fun calculateSplits_pause_twice_midway() {
        val testMeasurements = getMeasurementsArray(50000)
        val pauseAt = 25000
        val pauseTime = 30
        val testTimeStates = arrayListOf(
            TimeState(tripId = 1, state = TimeStateEnum.START, timestamp = 0L, false),
            TimeState(
                tripId = 1,
                state = TimeStateEnum.PAUSE,
                timestamp = testMeasurements[pauseAt].time + 1,
                false
            ),
            TimeState(
                tripId = 1,
                state = TimeStateEnum.RESUME,
                timestamp = testMeasurements[pauseAt + pauseTime].time,
                false
            ),
            TimeState(
                tripId = 1,
                state = TimeStateEnum.PAUSE,
                timestamp = testMeasurements[pauseAt + 500].time + 1,
                false
            ),
            TimeState(
                tripId = 1,
                state = TimeStateEnum.RESUME,
                timestamp = testMeasurements[pauseAt + 500 + pauseTime].time,
                false
            ),
            TimeState(
                tripId = 1,
                state = TimeStateEnum.STOP,
                timestamp = testMeasurements.last().time + 1,
                false
            ),
        )
        val splits = calculateSplits(
            1,
            testMeasurements,
            testTimeStates.toTypedArray(),
            METERS_TO_FEET * FEET_TO_MILES
        )
        Assert.assertEquals(32, splits.size)
        Assert.assertEquals(50000.0 - pauseTime * 2, splits.last().totalDistance, 0.01)
        Assert.assertEquals(50000.0 - pauseTime * 2, splits.last().totalDuration, 0.0)
    }

    @Test
    fun calculateSplits_pause_across_split() {
        val testMeasurements = getMeasurementsArray(50000)
        val pauseAt = (9.95 * 1 / FEET_TO_MILES * 1 / METERS_TO_FEET).roundToInt()
        val resumeAt = (10.05 * 1 / FEET_TO_MILES * 1 / METERS_TO_FEET).roundToInt()
        val testTimeStates = arrayListOf(
            TimeState(tripId = 1, state = TimeStateEnum.START, timestamp = 0L, false),
            TimeState(
                tripId = 1,
                state = TimeStateEnum.PAUSE,
                timestamp = testMeasurements[pauseAt].time + 1,
                false
            ),
            TimeState(
                tripId = 1,
                state = TimeStateEnum.RESUME,
                timestamp = testMeasurements[resumeAt].time,
                false
            ),
            TimeState(
                tripId = 1,
                state = TimeStateEnum.STOP,
                timestamp = testMeasurements.last().time + 1,
                false
            ),
        )
        val splits = calculateSplits(
            1,
            testMeasurements,
            testTimeStates.toTypedArray(),
            METERS_TO_FEET * FEET_TO_MILES
        )
        Assert.assertEquals(31, splits.size)
        Assert.assertEquals(50000.0 - (resumeAt - pauseAt), splits.last().totalDistance, 0.01)
        Assert.assertEquals(50000.0 - (resumeAt - pauseAt), splits.last().totalDuration, 0.0)
    }
}
