package com.kvl.cyclotrack

import com.kvl.cyclotrack.data.SearchExpression
import com.kvl.cyclotrack.data.applyNegation
import com.kvl.cyclotrack.data.compareDistanceExpression
import com.kvl.cyclotrack.data.compareMassExpression
import com.kvl.cyclotrack.data.compareSpeedExpression
import com.kvl.cyclotrack.data.parseDate
import com.kvl.cyclotrack.data.parseSearchString
import com.kvl.cyclotrack.data.tripPassesExpression
import com.kvl.cyclotrack.data.tripPassesExpressionString
import com.kvl.cyclotrack.util.FEET_TO_MILES
import com.kvl.cyclotrack.util.Kilogram
import com.kvl.cyclotrack.util.KilometersPerHour
import com.kvl.cyclotrack.util.METERS_TO_FEET
import com.kvl.cyclotrack.util.Meter
import com.kvl.cyclotrack.util.MetersPerSecond
import com.kvl.cyclotrack.util.Mile
import com.kvl.cyclotrack.util.MilesPerHour
import com.kvl.cyclotrack.util.Pound
import com.kvl.cyclotrack.util.Quantity
import com.kvl.cyclotrack.util.normalizeDistance
import com.kvl.cyclotrack.util.quantifyDistance
import org.junit.Assert
import org.junit.Test
import java.text.ParseException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoField


class SearchRegexTest {

    private fun dateTimeToInstant(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hour: Int = 0,
        minute: Int = 0,
        seconds: Int = 0
    ): Instant =
        LocalDateTime.of(year, month, dayOfMonth, hour, minute, seconds)
            .atZone(ZoneId.systemDefault())
            .toInstant()

    private fun dateToInstant(year: Int, month: Int, dayOfMonth: Int): Instant =
        dateTimeToInstant(year, month, dayOfMonth)


    private val sep192023 = dateTimeToInstant(2023, 9, 19)
    private val sep192022 = dateTimeToInstant(2022, 9, 19)

    private val tripTest20miles = Trip(
        name = "Test trip",
        distance = normalizeDistance(20.0, "1"),
        duration = 3600.0,
        averageSpeed = Quantity(20.0, MilesPerHour).convertTo(MetersPerSecond).float,
        inProgress = false,
        bikeId = 0,
        userWeight = Quantity(20.0, Pound).convertTo(Kilogram).float
    )
    private val trip19Sep2022 = tripTest20miles.copy(
        timestamp = sep192022.toEpochMilli()
    )
    private val trip19Sep2023 = tripTest20miles.copy(
        timestamp = sep192023.toEpochMilli()
    )

    @Test
    fun simpleDistanceSearchExpression() {
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "is",
                    rvalue = quantifyDistance(14.0, "1"),
                    junction = null
                )
            ),
            parseSearchString("distance is 14")
                .toTypedArray()
        )
    }

    @Test
    fun simpleMassSearchExpression() {
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "mass",
                    operator = "is",
                    rvalue = Quantity(170.0, Pound),
                    junction = null
                )
            ),
            parseSearchString("mass is 170")
                .toTypedArray()
        )
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "weight",
                    operator = "is",
                    rvalue = Quantity(170.0, Pound),
                    junction = null
                )
            ),
            parseSearchString("weight is 170")
                .toTypedArray()
        )
    }

    @Test
    fun simpleGreaterThanSearchExpression() {
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "greater than",
                    rvalue = quantifyDistance(50.0, "1"),
                    junction = null
                )
            ),
            parseSearchString("distance greater than 50")
                .toTypedArray()
        )
    }

    @Test
    fun simpleDateSearchExpression() {
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "date",
                    operator = "before",
                    rvalue = listOf(
                        dateToInstant(2023, 9, 18),
                        sep192023
                    ),
                    junction = null
                )
            ),
            parseSearchString("date before 2023-09-18")
                .toTypedArray()
        )
    }

    @Test
    fun simpleRangeSearchExpression() {
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "between",
                    rvalue = listOf(quantifyDistance(14.0, "1"), quantifyDistance(20.0, "1")),
                    junction = null
                )
            ),
            parseSearchString("distance between 14 and 20")
                .toTypedArray()
        )
    }

    @Test
    fun simpleTextSearchExpression() {
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "text",
                    operator = "contains",
                    rvalue = "flat tire",
                    junction = null
                )
            ),
            parseSearchString("text contains \"flat tire\"")
                .toTypedArray()
        )
    }

    @Test
    fun compoundSearchExpression() {
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "is",
                    rvalue = quantifyDistance(14.0, "1"),
                    junction = null
                ),
                SearchExpression(
                    negation = false,
                    lvalue = "date",
                    operator = "after",
                    rvalue = listOf(
                        dateToInstant(2023, 8, 4),
                        dateToInstant(2023, 8, 5)
                    ),
                    junction = "and"
                )
            ),
            parseSearchString("distance is 14 and date after 2023-08-04")
                .toTypedArray()
        )
    }

    @Test
    fun dateBefore() {
        val expressions = parseSearchString("date before 2023-08-04")
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "date",
                    operator = "before",
                    rvalue = listOf(
                        dateToInstant(2023, 8, 4),
                        dateToInstant(2023, 8, 5)
                    ),
                )
            ),
            expressions.toTypedArray()
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                trip19Sep2022,
                expressions
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                trip19Sep2023,
                expressions
            )
        )
    }

    @Test
    fun dateAfter() {
        val expressions = parseSearchString("date after 2023-08-04")
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "date",
                    operator = "after",
                    rvalue = listOf(
                        dateToInstant(2023, 8, 4),
                        dateToInstant(2023, 8, 5)
                    ),
                )
            ),
            expressions.toTypedArray()
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                trip19Sep2023,
                expressions
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                trip19Sep2022,
                expressions
            )
        )
    }

    @Test
    fun dateBetween() {
        val expressions = parseSearchString("date between 2023-08-04 and 2023-10-04")
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "date",
                    operator = "between",
                    rvalue = listOf(
                        listOf(
                            dateToInstant(2023, 8, 4),
                            dateToInstant(2023, 8, 5)
                        ),
                        listOf(
                            dateToInstant(2023, 10, 4),
                            dateToInstant(2023, 10, 5)
                        ),
                    ),
                )
            ),
            expressions.toTypedArray()
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                trip19Sep2023,
                expressions
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                trip19Sep2022,
                expressions
            )
        )
    }

    @Test
    fun dateBetweenMonthsNumeric() {
        val expressions = parseSearchString("date between 8-2023 and 10-2023")
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "date",
                    operator = "between",
                    rvalue = listOf(
                        listOf(
                            dateToInstant(2023, 8, 1),
                            dateToInstant(2023, 8, 31)
                        ),
                        listOf(
                            dateToInstant(2023, 10, 1),
                            dateToInstant(2023, 10, 31)
                        ),
                    ),
                )
            ),
            expressions.toTypedArray()
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                trip19Sep2023,
                expressions
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                trip19Sep2022,
                expressions
            )
        )
    }

    /*@Test
    fun dateBetweenMonthsText() {
        val expressions = parseSearchString("date between Aug-2023 and Oct-2023")
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "date",
                    operator = "between",
                    rvalue = listOf(
                        listOf(
                            dateToInstant(2023, 8, 1),
                            dateToInstant(2023, 8, 31)
                        ),
                        listOf(
                            dateToInstant(2023, 10, 1),
                            dateToInstant(2023, 10, 31)
                        ),
                    ),
                )
            ),
            expressions.toTypedArray()
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                trip19Sep2023,
                expressions
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                trip19Sep2022,
                expressions
            )
        )
    }*/

    @Test
    fun justDate() {
        val expressions = parseSearchString("2023-08-04")
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "date",
                    operator = "is",
                    rvalue = listOf(
                        dateToInstant(2023, 8, 4),
                        dateToInstant(2023, 8, 5)
                    ),
                )
            ),
            expressions.toTypedArray()
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                tripTest20miles.copy(
                    timestamp = dateTimeToInstant(
                        2023,
                        8,
                        4,
                        hour = 12
                    ).toEpochMilli()
                ),
                expressions
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                trip19Sep2022,
                expressions
            )
        )
    }

    @Test
    fun justMonthYear() {
        val expressions = parseSearchString("September 2023")
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "date",
                    operator = "is",
                    rvalue = listOf(
                        dateToInstant(2023, 9, 1),
                        dateToInstant(2023, 9, 30)
                    ),
                )
            ),
            expressions.toTypedArray()
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                trip19Sep2023,
                expressions
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                trip19Sep2022,
                expressions
            )
        )
    }

    @Test
    fun justMonth() {
        val expressions = parseSearchString("September")
        val currentYear = ZonedDateTime.now().get(ChronoField.YEAR)
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "date",
                    operator = "is",
                    rvalue = listOf(
                        dateToInstant(currentYear, 9, 1),
                        dateToInstant(currentYear, 9, 30)
                    ),
                )
            ),
            expressions.toTypedArray()
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                tripTest20miles.copy(timestamp = dateToInstant(currentYear, 9, 9).toEpochMilli()),
                expressions
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                trip19Sep2022,
                expressions
            )
        )
    }

    @Test
    fun justNumber() {
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    lvalue = "distance",
                    operator = "is",
                    rvalue = quantifyDistance(20.0, "miles")
                )
            ), parseSearchString("20").toTypedArray()
        )
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    lvalue = "distance",
                    operator = "is",
                    rvalue = quantifyDistance(20.1, "miles")
                )
            ), parseSearchString("20.1 miles").toTypedArray()
        )
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    lvalue = "speed",
                    operator = "is",
                    rvalue = Quantity(20.1, MilesPerHour)
                )
            ), parseSearchString("20.1 mph").toTypedArray()
        )
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    lvalue = "speed",
                    operator = "is",
                    rvalue = Quantity(20.1, KilometersPerHour)
                )
            ), parseSearchString("20.1 kph").toTypedArray()
        )
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    lvalue = "speed",
                    operator = "is",
                    rvalue = Quantity(20.1, KilometersPerHour)
                )
            ), parseSearchString("20.1 km/h").toTypedArray()
        )
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    lvalue = "mass",
                    operator = "is",
                    rvalue = Quantity(170.0, Pound)
                )
            ), parseSearchString("170 pounds").toTypedArray()
        )
    }

    @Test
    fun justText() {
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    lvalue = "text",
                    operator = "contains",
                    rvalue = "evening"
                )
            ), parseSearchString("evening").toTypedArray()
        )
    }

    @Test
    fun tripleCompoundRangeSearchExpression() {
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "between",
                    rvalue = listOf(
                        quantifyDistance(10.0, "1"),
                        quantifyDistance(20.0, "1")
                    ),
                    junction = null
                ),
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "between",
                    rvalue = listOf(
                        quantifyDistance(15.0, "1"),
                        quantifyDistance(17.0, "1")
                    ),
                    junction = "and"
                ),
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "between",
                    rvalue = listOf(
                        quantifyDistance(13.0, "1"),
                        quantifyDistance(18.0, "1")
                    ),
                    junction = "and"
                ),
            ),

            parseSearchString("distance between 10 and 20 and distance between 15 and 17 and distance between 13 and 18")
                .toTypedArray()
        )
    }

    @Test
    fun tripleCompoundSearchExpression() {
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "is",
                    rvalue = quantifyDistance(14.0, "1"),
                    junction = null
                ),
                SearchExpression(
                    negation = false,
                    lvalue = "date",
                    operator = "after",
                    rvalue = listOf(
                        dateToInstant(2023, 8, 4),
                        dateToInstant(2023, 8, 5)
                    ),
                    junction = "and"
                ),
                SearchExpression(
                    negation = false,
                    lvalue = "text",
                    operator = "contains",
                    rvalue = "flat tire",
                    junction = "and"
                )
            ),
            parseSearchString("distance is 14 and date after 2023-08-04 and text contains \"flat tire\"")
                .toTypedArray()
        )
    }

    @Test
    fun compareDistanceExpressionTest() {
        Assert.assertEquals(
            true, compareDistanceExpression(
                tripTest20miles,
                SearchExpression(
                    lvalue = "distance",
                    operator = "is",
                    rvalue = quantifyDistance(20.0, "1")
                )
            )
        )
    }

    @Test
    fun compareMassExpressionTest() {
        Assert.assertEquals(
            true, compareMassExpression(
                tripTest20miles.copy(
                    userWeight = Quantity(
                        20.0,
                        Pound
                    ).convertTo(Kilogram).float
                ),
                SearchExpression(
                    lvalue = "mass",
                    operator = "is",
                    rvalue = Quantity(20.0, Pound)
                )
            )
        )
    }

    @Test
    fun compareSpeedExpressionTest() {
        Assert.assertEquals(
            true, compareSpeedExpression(
                tripTest20miles,
                SearchExpression(
                    lvalue = "speed",
                    operator = "is",
                    rvalue = Quantity(20.0, MilesPerHour)
                )
            )
        )
    }

    @Test
    fun applyNegationTest() {
        Assert.assertEquals(
            true, applyNegation(
                value = true,
                negation = false
            )
        )
        Assert.assertEquals(
            true, applyNegation(
                value = false,
                negation = true
            )
        )
        Assert.assertEquals(
            false, applyNegation(
                value = false,
                negation = false
            )
        )
        Assert.assertEquals(
            false, applyNegation(
                value = true,
                negation = true
            )
        )
    }

    @Test
    fun simpleTripExpressionTest() {
        Assert.assertEquals(
            true, tripPassesExpression(
                tripTest20miles, listOf(
                    SearchExpression(
                        lvalue = "distance",
                        operator = "is",
                        rvalue = quantifyDistance(20.0, "1")
                    )
                )
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                tripTest20miles, listOf(
                    SearchExpression(
                        negation = true,
                        lvalue = "distance",
                        operator = "is",
                        rvalue = quantifyDistance(20.0, "1")
                    )
                )
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                tripTest20miles.copy(
                    distance = 19.0 / (METERS_TO_FEET * FEET_TO_MILES)
                ), listOf(
                    SearchExpression(
                        lvalue = "distance",
                        operator = "is",
                        rvalue = quantifyDistance(20.0, "1")
                    )
                )
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                tripTest20miles.copy(
                    distance = 21.0 / (METERS_TO_FEET * FEET_TO_MILES)
                ), listOf(
                    SearchExpression(
                        lvalue = "distance",
                        operator = "is",
                        rvalue = quantifyDistance(20.0, "1")
                    )
                )
            )
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                tripTest20miles.copy(distance = 19.0 / (METERS_TO_FEET * FEET_TO_MILES)), listOf(
                    SearchExpression(
                        lvalue = "distance",
                        operator = "less than",
                        rvalue = Quantity(20.0, Mile)
                    )
                )
            )
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                tripTest20miles.copy(
                    distance = 21.0 / (METERS_TO_FEET * FEET_TO_MILES),
                ), listOf(
                    SearchExpression(
                        lvalue = "distance",
                        operator = "greater than",
                        rvalue = Quantity(20.0, Mile)
                    )
                )
            )
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                tripTest20miles.copy(
                    distance = 21.0 / (METERS_TO_FEET * FEET_TO_MILES),
                ), listOf(
                    SearchExpression(
                        lvalue = "distance",
                        operator = "between",
                        rvalue = listOf(
                            quantifyDistance(20.9, "1"),
                            quantifyDistance(21.1, "1")
                        )
                    )
                )
            )
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                tripTest20miles, listOf(
                    SearchExpression(
                        lvalue = "speed",
                        operator = "is",
                        rvalue = Quantity(20.0, MilesPerHour)
                    )
                )
            )
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                tripTest20miles, listOf(
                    SearchExpression(
                        lvalue = "text",
                        operator = "contains",
                        rvalue = "Test trip"
                    )
                )
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                tripTest20miles, listOf(
                    SearchExpression(
                        lvalue = "text",
                        operator = "contains",
                        rvalue = "flat tire"
                    )
                )
            )
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                tripTest20miles.copy(
                    name = "flat tire"
                ), listOf(
                    SearchExpression(
                        lvalue = "text",
                        operator = "contains",
                        rvalue = "flat tire"
                    )
                )
            )
        )
    }

    @Test
    fun compoundTripExpressionTrueTest() {
        Assert.assertEquals(
            true, tripPassesExpression(
                tripTest20miles, listOf(
                    SearchExpression(
                        lvalue = "distance",
                        operator = "is",
                        rvalue = quantifyDistance(20.0, "1")
                    ),
                    SearchExpression(
                        lvalue = "text",
                        operator = "contains",
                        rvalue = "Test",
                        junction = "and"
                    )
                )
            )
        )
    }

    @Test
    fun compoundTripExpressionFalseTest() {
        Assert.assertEquals(
            false, tripPassesExpression(
                tripTest20miles, listOf(
                    SearchExpression(
                        lvalue = "distance",
                        operator = "is",
                        rvalue = Quantity(20.0, Mile)
                    ),
                    SearchExpression(
                        junction = "and",
                        lvalue = "text",
                        operator = "contains",
                        rvalue = "flat tire"
                    )
                )
            )
        )
    }

    @Test
    fun dateTests() {
        Assert.assertEquals(
            true, tripPassesExpression(
                trip19Sep2023.copy(
                    timestamp = dateTimeToInstant(2023, 9, 19, 12, 30)
                        .toEpochMilli(),
                ), listOf(
                    SearchExpression(
                        lvalue = "date",
                        operator = "is",
                        rvalue = listOf(
                            dateToInstant(2023, 9, 19),
                            dateToInstant(2023, 9, 20)
                        )
                    ),
                )
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                tripTest20miles.copy(
                    timestamp = dateTimeToInstant(2023, 9, 19, 12, 30)
                        .toEpochMilli(),
                ), listOf(
                    SearchExpression(
                        lvalue = "date",
                        operator = "is",
                        rvalue = listOf(
                            dateToInstant(2023, 8, 19),
                            dateToInstant(2023, 8, 20)
                        )
                    ),
                )
            )
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                trip19Sep2023, listOf(
                    SearchExpression(
                        lvalue = "date",
                        operator = "before",
                        rvalue = listOf(
                            dateToInstant(2023, 9, 20),
                            dateToInstant(2023, 9, 21),
                        )
                    ),
                )
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                trip19Sep2023, listOf(
                    SearchExpression(
                        lvalue = "date",
                        operator = "before",
                        rvalue = listOf(
                            dateToInstant(2023, 8, 18),
                            dateToInstant(2023, 8, 19)
                        )
                    ),
                )
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                trip19Sep2023, listOf(
                    SearchExpression(
                        lvalue = "date",
                        operator = "after",
                        rvalue = listOf(
                            dateToInstant(2023, 10, 19),
                            dateToInstant(2023, 10, 20)
                        )
                    ),
                )
            )
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                trip19Sep2023, listOf(
                    SearchExpression(
                        lvalue = "date",
                        operator = "after",
                        rvalue = listOf(
                            dateToInstant(2023, 8, 19),
                            dateToInstant(2023, 8, 20),
                        )
                    ),
                )
            )
        )
        Assert.assertEquals(
            true, tripPassesExpression(
                trip19Sep2023, listOf(
                    SearchExpression(
                        lvalue = "date",
                        operator = "between",
                        rvalue = listOf(
                            listOf(
                                dateToInstant(2023, 8, 19),
                                dateToInstant(2023, 8, 20)
                            ),
                            listOf(
                                dateToInstant(2023, 10, 19),
                                dateToInstant(2023, 10, 20)
                            ),
                        )
                    ),
                )
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                tripTest20miles.copy(
                    timestamp = dateToInstant(2023, 7, 19).toEpochMilli()
                ), listOf(
                    SearchExpression(
                        lvalue = "date",
                        operator = "between",
                        rvalue = listOf(
                            listOf(
                                dateToInstant(2023, 8, 19),
                                dateToInstant(2023, 8, 20)
                            ),
                            listOf(
                                dateToInstant(2023, 10, 19),
                                dateToInstant(2023, 10, 20)
                            ),
                        )
                    ),
                )
            )
        )
        Assert.assertEquals(
            false, tripPassesExpression(
                tripTest20miles.copy(
                    timestamp = sep192023.toEpochMilli(),
                ), listOf(
                    SearchExpression(
                        lvalue = "date",
                        operator = "between",
                        rvalue = listOf(
                            listOf(
                                dateToInstant(2022, 8, 1),
                                dateToInstant(2022, 8, 2)
                            ),
                            listOf(
                                dateToInstant(2022, 10, 30),
                                dateToInstant(2022, 11, 1)
                            ),
                        )
                    ),
                )
            )
        )
    }

    @Test
    fun tripPassesExpressionStringDistanceTest() {
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "distance is 20",
                tripTest20miles,
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "distance is 20",
                tripTest20miles.copy(distance = Quantity(20.4, Mile).convertTo(Meter).value),
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "distance is 20",
                tripTest20miles.copy(distance = Quantity(19.6, Mile).convertTo(Meter).value),
            )
        )
        Assert.assertEquals(
            false, tripPassesExpressionString(
                "distance is 20",
                tripTest20miles.copy(distance = Quantity(20.6, Mile).convertTo(Meter).value),
            )
        )
        Assert.assertEquals(
            false, tripPassesExpressionString(
                "distance is 20",
                tripTest20miles.copy(distance = Quantity(19.4, Mile).convertTo(Meter).value),
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "20 miles",
                tripTest20miles,
            )
        )
    }

    @Test
    fun tripPassesExpressionStringSpeedTest() {
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "speed is 20",
                tripTest20miles,
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "speed is 20",
                tripTest20miles.copy(
                    averageSpeed = Quantity(
                        20.09,
                        MilesPerHour
                    ).convertTo(Meter).float
                ),
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "speed is 20",
                tripTest20miles.copy(
                    averageSpeed = Quantity(
                        19.91,
                        MilesPerHour
                    ).convertTo(Meter).float
                ),
            )
        )
        Assert.assertEquals(
            false, tripPassesExpressionString(
                "speed is 20",
                tripTest20miles.copy(
                    averageSpeed = Quantity(
                        20.6,
                        MilesPerHour
                    ).convertTo(Meter).float
                ),
            )
        )
        Assert.assertEquals(
            false, tripPassesExpressionString(
                "speed is 20",
                tripTest20miles.copy(
                    averageSpeed = Quantity(
                        19.4,
                        MilesPerHour
                    ).convertTo(Meter).float
                ),
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "20 miles",
                tripTest20miles,
            )
        )
    }

    @Test
    fun tripPassesExpressionStringMassTest() {
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "mass is 20",
                tripTest20miles,
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "mass is 20",
                tripTest20miles.copy(
                    userWeight = Quantity(
                        20.09,
                        Pound
                    ).convertTo(Kilogram).float
                ),
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "mass is 20",
                tripTest20miles.copy(
                    userWeight = Quantity(
                        19.91,
                        Pound
                    ).convertTo(Kilogram).float
                ),
            )
        )
        Assert.assertEquals(
            false, tripPassesExpressionString(
                "mass is 20",
                tripTest20miles.copy(
                    userWeight = Quantity(
                        20.6,
                        Pound
                    ).convertTo(Kilogram).float
                ),
            )
        )
        Assert.assertEquals(
            false, tripPassesExpressionString(
                "mass is 20",
                tripTest20miles.copy(
                    userWeight = Quantity(
                        19.4,
                        Pound
                    ).convertTo(Kilogram).float
                ),
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "20 miles",
                tripTest20miles,
            )
        )
    }

    @Test
    fun tripPassesExpressionStringDateEqualsTest() {
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "date is 2023-09-09",
                tripTest20miles.copy(
                    timestamp = dateTimeToInstant(2023, 9, 9, 7, 5)
                        .toEpochMilli(),
                ),
            )
        )
    }

    @Test
    fun tripPassesExpressionStringDateRangeTest() {
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "date between 2022-09-01 and 2022-09-30",
                trip19Sep2022,
            )
        )
        Assert.assertEquals(
            false, tripPassesExpressionString(
                "date between 2023-09-01 and 2023-09-30",
                trip19Sep2022,
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "date is Sep-2022",
                trip19Sep2022,
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "date is 9/2022",
                trip19Sep2022,
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "date is 9-2022",
                trip19Sep2022,
            )
        )
        Assert.assertEquals(
            false, tripPassesExpressionString(
                "date is 'Aug 2022'",
                trip19Sep2022,
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "date is 'Sep 2022'",
                trip19Sep2022,
            )
        )
    }

    @Test
    fun tripPassesExpressionStringTextTest() {
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "details contains extra",
                tripTest20miles.copy(
                    notes = "This ride was extra"
                ),
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "description contains extra",
                tripTest20miles.copy(
                    notes = "This ride was extra"
                ),
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "notes contains extra",
                tripTest20miles.copy(
                    notes = "This ride was extra"
                ),
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "text contains extra",
                tripTest20miles.copy(
                    notes = "This ride was extra"
                ),
            )
        )
        Assert.assertEquals(
            false, tripPassesExpressionString(
                "title contains extra",
                tripTest20miles.copy(
                    name = "brakes tires shifters seat",
                    notes = "This ride was extra"
                ),
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "name contains shifters",
                tripTest20miles.copy(
                    name = "brakes tires shifters seat",
                    notes = "This ride was extra"
                ),
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "title contains brakes",
                tripTest20miles.copy(
                    name = "brakes tires shifters seat",
                    notes = "This ride was extra"
                ),
            )
        )
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "text contains tires",
                tripTest20miles.copy(
                    name = "brakes tires shifters seat",
                    notes = "This ride was extra"
                ),
            )
        )
        Assert.assertEquals(
            false, tripPassesExpressionString(
                "text contains everything",
                tripTest20miles.copy(
                    name = "brakes tires shifters seat",
                    notes = "This ride was extra"
                ),
            )
        )
    }

    @Test
    fun parseDateTest() {
        Assert.assertEquals(
            listOf(
                dateToInstant(2022, 1, 1),
                dateToInstant(2022, 1, 2)
            ),
            parseDate("2022-01-01")
        )
        Assert.assertEquals(
            listOf(
                sep192022,
                dateToInstant(2022, 9, 20)
            ),
            parseDate("2022-09-19")
        )
        Assert.assertEquals(
            listOf(
                dateToInstant(2022, 1, 1),
                dateToInstant(2022, 1, 2),
            ),
            parseDate("Jan 1 2022")
        )
        Assert.assertEquals(
            listOf(
                dateToInstant(2022, 9, 19),
                dateToInstant(2022, 9, 20)
            ),
            parseDate("Sep 19 2022")
        )
        Assert.assertEquals(
            listOf(
                dateToInstant(2022, 9, 1),
                dateToInstant(2022, 9, 30)
            ),
            parseDate("9 2022")
        )
        Assert.assertEquals(
            listOf(
                dateToInstant(2022, 9, 1),
                dateToInstant(2022, 9, 30)
            ),
            parseDate("Sep 2022")
        )
        Assert.assertEquals(
            listOf(
                dateToInstant(2022, 1, 1),
                dateToInstant(2022, 12, 31)
            ),
            parseDate("2022")
        )
        Assert.assertEquals(
            listOf(
                dateToInstant(2023, 9, 1),
                dateToInstant(2023, 9, 30)
            ),
            parseDate("Sep")
        )
        Assert.assertThrows(ParseException::class.java) {
            parseDate("evening")
        }
    }

    @Test
    fun numberStringRegex() {
        var result = com.kvl.cyclotrack.data.numberStringRegex.find("20")
        Assert.assertEquals("20", result!!.groups["value"]!!.value)

        result = com.kvl.cyclotrack.data.numberStringRegex.find("20 miles")
        Assert.assertEquals("20", result!!.groups["value"]!!.value)
        Assert.assertEquals("miles", result.groups["units"]!!.value)

        result = com.kvl.cyclotrack.data.numberStringRegex.find("20.1 miles")
        Assert.assertEquals("20.1", result!!.groups["value"]!!.value)
        Assert.assertEquals("miles", result.groups["units"]!!.value)

        result = com.kvl.cyclotrack.data.numberStringRegex.find("20.1 km")
        Assert.assertEquals("20.1", result!!.groups["value"]!!.value)
        Assert.assertEquals("km", result.groups["units"]!!.value)
    }

    @Test
    fun badQueries() {
        Assert.assertEquals(
            false, tripPassesExpressionString(
                "date falalal werwoij",
                trip19Sep2022,
            )
        )
        Assert.assertEquals(
            false, tripPassesExpressionString(
                "asdfawe falalal werwoij",
                trip19Sep2022,
            )
        )
        Assert.assertThrows(
            ParseException::class.java
        ) {
            tripPassesExpressionString(
                "date is candy",
                trip19Sep2022,
            )
        }
        Assert.assertThrows(NumberFormatException::class.java) {
            tripPassesExpressionString(
                "distance is candy",
                trip19Sep2022,
            )
        }
        Assert.assertThrows(NumberFormatException::class.java) {
            tripPassesExpressionString(
                "distance is 1 and 2",
                trip19Sep2022,
            )
        }
        Assert.assertThrows(ParseException::class.java) {
            tripPassesExpressionString(
                "text is foo",
                trip19Sep2022,
            )
        }
    }
}