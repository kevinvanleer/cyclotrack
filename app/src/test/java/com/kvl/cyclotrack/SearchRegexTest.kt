package com.kvl.cyclotrack

import com.kvl.cyclotrack.data.SearchExpression
import com.kvl.cyclotrack.data.applyNegation
import com.kvl.cyclotrack.data.compareDistanceExpression
import com.kvl.cyclotrack.data.expressionRegex
import com.kvl.cyclotrack.data.tripPassesExpression
import org.junit.Assert
import org.junit.Test
import java.time.LocalDate


class SearchRegexTest {
    @Test
    fun simpleDistanceSearchExpression() {
        Assert.assertEquals(
            listOf(
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "is",
                    rvalue = 14.0,
                    junction = null
                )
            ),
            expressionRegex.findAll("distance is 14")
                .map {
                    SearchExpression(it.groups)
                }.toList()
        )
    }

    @Test
    fun simpleGreaterThanSearchExpression() {
        Assert.assertEquals(
            listOf(
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "greater than",
                    rvalue = 50.0,
                    junction = null
                )
            ),
            expressionRegex.findAll("distance greater than 50")
                .map {
                    SearchExpression(it.groups)
                }.toList()
        )
    }

    @Test
    fun simpleDateSearchExpression() {
        Assert.assertEquals(
            listOf(
                SearchExpression(
                    negation = false,
                    lvalue = "date",
                    operator = "before",
                    rvalue = LocalDate.of(2023, 9, 18),
                    junction = null
                )
            ),
            expressionRegex.findAll("date before 2023-09-18")
                .map {
                    SearchExpression(it.groups)
                }.toList()
        )
    }

    @Test
    fun simpleRangeSearchExpression() {
        Assert.assertEquals(
            listOf(
                SearchExpression(
                    negation = false,
                    lvalue = "distance",
                    operator = "between",
                    rvalue = listOf(14.0, 20.0),
                    junction = null
                )
            ),
            expressionRegex.findAll("distance between 14 and 20")
                .map {
                    SearchExpression(it.groups)
                }.toList()
        )
    }

    @Test
    fun simpleTextSearchExpression() {
        Assert.assertEquals(
            listOf(
                SearchExpression(
                    negation = false,
                    lvalue = "text",
                    operator = "contains",
                    rvalue = "flat tire",
                    junction = null
                )
            ),
            expressionRegex.findAll("text contains \"flat tire\"")
                .map {
                    SearchExpression(it.groups)
                }.toList()
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
                    rvalue = LocalDate.of(2023, 8, 4),
                    junction = "and"
                )
            ),
            expressionRegex.findAll("distance is 14 and date after 2023-08-04")
                .map {
                    SearchExpression(it.groups)
                }.toList().toTypedArray()
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
                    rvalue = LocalDate.of(2023, 8, 4),
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
            expressionRegex.findAll("distance is 14 and date after 2023-08-04 and text contains \"flat tire\"")
                .map {
                    SearchExpression(it.groups)
                }.toList().toTypedArray()
        )
    }

    @Test
    fun compareDistanceExpressionTest() {
        Assert.assertEquals(
            true, compareDistanceExpression(
                Trip(
                    name = "Test trip",
                    distance = 20.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
                ),
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
                Trip(
                    name = "Test trip",
                    distance = 20.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
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
                Trip(
                    name = "Test trip",
                    distance = 20.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
                ), listOf(
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
                Trip(
                    name = "Test trip",
                    distance = 19.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
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
                Trip(
                    name = "Test trip",
                    distance = 21.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
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
                Trip(
                    name = "Test trip",
                    distance = 19.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
                ), listOf(
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
                Trip(
                    name = "Test trip",
                    distance = 21.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
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
                Trip(
                    name = "Test trip",
                    distance = 21.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
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
                Trip(
                    name = "Test trip",
                    distance = 21.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
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
                Trip(
                    name = "Test trip",
                    distance = 21.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
                ), listOf(
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
                Trip(
                    name = "flat tire",
                    distance = 21.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
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
                Trip(
                    name = "Test trip",
                    distance = 20.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
                ), listOf(
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
                Trip(
                    name = "Test trip",
                    distance = 20.0 / (METERS_TO_FEET * FEET_TO_MILES),
                    duration = 3600.0,
                    averageSpeed = 20.0f,
                    inProgress = false,
                    bikeId = 0,
                ), listOf(
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
}