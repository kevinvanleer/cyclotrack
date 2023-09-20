package com.kvl.cyclotrack.data

import com.kvl.cyclotrack.FEET_TO_MILES
import com.kvl.cyclotrack.METERS_TO_FEET
import com.kvl.cyclotrack.Trip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val expressionRegex =
    Regex("""(?<junction>and|or)?\s?(?<negation>not)?\s?(?<lvalue>distance|date|text) (?<operator>contains|is|equals|less than|greater than|before|after|between) (?<rvalue>\".*?\"|\'.*?\'|[0-9]+ (and|or) [0-9]+|\S+)\s?""")

fun parseSearchString(searchString: String) =
    expressionRegex.findAll(searchString)
        .map {
            SearchExpression(it.groups)
        }.toList()

fun tripPassesExpression(trip: Trip, searchExpressions: List<SearchExpression>): Boolean =
    searchExpressions.fold(false) { result, expression ->
        when (expression.lvalue.lowercase()) {
            "distance" ->
                compareDistanceExpression(trip, expression)

            "date" -> compareDateExpression(trip, expression)
            "text" -> trip.name?.contains(expression.rvalue.toString()) == true || trip.notes?.contains(
                expression.rvalue.toString()
            ) == true

            else -> false
        }.let {
            applyNegation(it, expression.negation)
        }.let {
            when (expression.junction?.lowercase()) {
                "and" -> it && result
                else -> it || result
            }
        }
    }

fun applyNegation(value: Boolean, negation: Boolean) = when (negation) {
    true -> !value
    else -> value
}

fun compareDateExpression(
    trip: Trip,
    expression: SearchExpression,
): Boolean =
    when (expression.operator) {
        "is", "equals" -> Instant.ofEpochMilli(trip.timestamp) == expression.rvalue
        "greater than", "after" -> Instant.ofEpochMilli(trip.timestamp)
            .isAfter(expression.rvalue as Instant)

        "less than", "before" -> Instant.ofEpochMilli(trip.timestamp)
            .isBefore(expression.rvalue as Instant)

        "between" -> Instant.ofEpochMilli(trip.timestamp)
            .isAfter((expression.rvalue as List<*>)[0] as Instant)
                && Instant.ofEpochMilli(trip.timestamp)
            .isBefore((expression.rvalue)[1] as Instant)

        else -> Instant.ofEpochMilli(trip.timestamp) == expression.rvalue
    }

fun compareDistanceExpression(
    trip: Trip,
    expression: SearchExpression,
): Boolean {
    val milesToMeters = 1 / (METERS_TO_FEET * FEET_TO_MILES)
    val delta = milesToMeters / 2
    fun targetDistance(dist: Any) = dist.toString().toDouble().times(milesToMeters)
    return when (expression.operator.lowercase()) {
        "is", "equals" ->
            ((targetDistance(expression.rvalue) - delta) <= trip.distance!!) && ((targetDistance(
                expression.rvalue
            ) + delta) > trip.distance)

        "greater than" -> trip.distance!! > targetDistance(expression.rvalue)
        "less than" -> trip.distance!! < targetDistance(expression.rvalue)
        "between" -> trip.distance!! >= targetDistance((expression.rvalue as List<*>)[0] as Double) &&
                trip.distance <= targetDistance(expression.rvalue[1] as Double)

        else -> false
    }
}

data class SearchExpression(
    val negation: Boolean = false,
    val lvalue: String,
    val operator: String,
    val rvalue: Any,
    val junction: String? = null
) {
    constructor(regexMatchGroups: MatchGroupCollection) : this(
        !regexMatchGroups["negation"]?.value.isNullOrEmpty(),
        regexMatchGroups["lvalue"]!!.value,
        regexMatchGroups["operator"]!!.value,
        when (regexMatchGroups["operator"]!!.value.lowercase()) {
            "between" -> Regex("""(?<lower>[0-9]+) and (?<upper>[0-9]+)""")
                .find(regexMatchGroups["rvalue"]!!.value)?.groups?.let {
                    arrayOf(it["lower"]!!.value, it["upper"]!!.value)
                }

            else -> Regex("""^['"]?(?<string>.*?)['"]?$""")
                .find(regexMatchGroups["rvalue"]!!.value)?.groups?.let {
                    arrayOf(it["string"]!!.value)
                }
        }!!.map { rvalue ->
            when (regexMatchGroups["lvalue"]!!.value.lowercase()) {
                "distance" -> rvalue.toDouble()
                "date" -> LocalDate.parse(rvalue).atStartOfDay(
                    ZoneId.systemDefault()
                ).toInstant()

                else -> rvalue
            }
        }.let { typedArray ->
            when (typedArray.size) {
                1 -> typedArray[0]
                else -> typedArray
            }
        },
        regexMatchGroups["junction"]?.value,
    )
}