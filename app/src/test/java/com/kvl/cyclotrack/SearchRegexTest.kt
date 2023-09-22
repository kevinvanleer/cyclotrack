package com.kvl.cyclotrack

import com.kvl.cyclotrack.data.SearchExpression
import com.kvl.cyclotrack.data.applyNegation
import com.kvl.cyclotrack.data.compareDistanceExpression
import com.kvl.cyclotrack.data.parseDate
import com.kvl.cyclotrack.data.parseSearchString
import com.kvl.cyclotrack.data.tripPassesExpression
import com.kvl.cyclotrack.data.tripPassesExpressionString
import org.junit.Assert
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId


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
        distance = 20.0 / (METERS_TO_FEET * FEET_TO_MILES),
        duration = 3600.0,
        averageSpeed = 20.0f,
        inProgress = false,
        bikeId = 0,
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
                    rvalue = 14.0,
                    junction = null
                )
            ),
            parseSearchString("distance is 14")
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
                    rvalue = 50.0,
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
                    rvalue = listOf(14.0, 20.0),
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
                    rvalue = 14.0,
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
    fun tripleCompoundRangeSearchExpression() {
        Assert.assertArrayEquals(
            arrayOf(
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "between",
                    rvalue = listOf(10.0, 20.0),
                    junction = null
                ),
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "between",
                    rvalue = listOf(15.0, 17.0),
                    junction = "and"
                ),
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "between",
                    rvalue = listOf(13.0, 18.0),
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
                    rvalue = 14.0,
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
                    rvalue = 20.0
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
                        rvalue = 20.0
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
                        rvalue = 20.0
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
                        rvalue = 20.0
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
                        rvalue = 20.0
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
                        rvalue = 20.0
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
                        rvalue = 20.0
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
                        rvalue = listOf(20.9, 21.1)
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
                        rvalue = 20.0
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
                        rvalue = 20.0
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
                        rvalue = dateToInstant(2023, 9, 20)
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
                        rvalue = dateToInstant(2023, 8, 18)
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
                        rvalue = dateToInstant(2023, 10, 19)
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
                        rvalue = dateToInstant(2023, 8, 19)
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
    fun tripPassesExpressionStringTest() {
        Assert.assertEquals(
            true, tripPassesExpressionString(
                "distance is 20",
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
    }
}