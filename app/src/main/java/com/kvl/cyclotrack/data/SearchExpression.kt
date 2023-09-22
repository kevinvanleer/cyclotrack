package com.kvl.cyclotrack.data

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.FEET_TO_MILES
import com.kvl.cyclotrack.FeatureFlags.Companion.devBuild
import com.kvl.cyclotrack.METERS_TO_FEET
import com.kvl.cyclotrack.Trip
import com.kvl.cyclotrack.util.dateFormatPattenDob
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

val numberStringRegex = Regex("""^\s*(?<value>\d+.?\d+?)( (?<units>\S+)\s*)?$""")
val expressionRegex =
    Regex("""(?<junction>and|or)?\s?(?<negation>not)?\s?(?<lvalue>distance|date|text) (?<operator>contains|is|equals|less than|greater than|before|after|between) (?<rvalue>\".*?\"|\'.*?\'|[\d\-/]+ (and|or) [\d\-/]+|\S+)\s?""")

fun unitsToSystem(units: String?): String {
    return when (units) {
        "kilometers", "km", "cm", "C", "km/h" -> "2"
        "miles", "mile", "mi", "F", "mph", "m/h", "in", "inch", "inches", "ft", "feet" -> "1"
        else -> "0"
    }
}

fun distanceToMeters(distance: Double, measurementSystem: String) =
    when (measurementSystem) {
        "miles", "mile", "mi", "1" -> distance / (METERS_TO_FEET * FEET_TO_MILES)
        "kilometers", "km", "2" -> distance * 1000
        else -> distance
    }

fun parseSearchString(
    searchString: String,
    measurementSystem: String = "1"
): List<SearchExpression> {
    try {
        return listOf(
            SearchExpression(
                lvalue = "date",
                operator = "is",
                rvalue = parseDate(searchString)
            )
        )
    } catch (_: Exception) {

    }
    try {
        numberStringRegex.find(searchString)?.let {
            return listOf(
                SearchExpression(
                    lvalue = "distance",
                    operator = "is",
                    rvalue = distanceToMeters(
                        it.groups["value"]!!.value.toDouble(),
                        it.groups["units"]?.value ?: measurementSystem
                    )
                )
            )
        }
    } catch (_: Exception) {

    }
    try {
        return listOf(
            SearchExpression(
                lvalue = "distance",
                operator = "is",
                rvalue = searchString.toDouble()
            )
        )
    } catch (_: Exception) {

    }

    try {
        return expressionRegex.findAll(searchString)
            .map {
                SearchExpression(it.groups)
            }.toList()
    } catch (_: Exception) {

    }

    return listOf(
        SearchExpression(
            lvalue = "text",
            operator = "contains",
            rvalue = searchString
        )
    )
}

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

fun tripPassesExpressionString(searchString: String, trip: Trip) =
    tripPassesExpression(trip, parseSearchString(searchString))

fun applyNegation(value: Boolean, negation: Boolean) = when (negation) {
    true -> !value
    else -> value
}

fun compareDateExpression(
    trip: Trip,
    expression: SearchExpression,
): Boolean =
    when (expression.operator) {
        "greater than", "after" -> Instant.ofEpochMilli(trip.timestamp)
            .isAfter((expression.rvalue as List<*>)[1] as Instant)

        "less than", "before" -> Instant.ofEpochMilli(trip.timestamp)
            .isBefore((expression.rvalue as List<*>)[0] as Instant)

        "is" -> Instant.ofEpochMilli(trip.timestamp).let { ts ->
            (expression.rvalue as List<*>).let { rvalue ->
                ts.isAfter(rvalue[0] as Instant) &&
                        ts.isBefore(rvalue[1] as Instant)
            }
        }

        "between" -> (expression.rvalue as List<*>).let { range ->
            Instant.ofEpochMilli(trip.timestamp).let { ts ->
                ts.isAfter((range[0] as List<*>)[0] as Instant)
                        &&
                        ts.isBefore((range[1] as List<*>)[1] as Instant)
            }
        }

        else -> Instant.ofEpochMilli(trip.timestamp) == expression.rvalue
    }

fun compareDistanceExpression(
    trip: Trip,
    expression: SearchExpression,
): Boolean {
    fun targetDistance(dist: Double) = dist
    return when (expression.operator.lowercase()) {
        "is", "equals" -> {
            val milesToMeters = 1 / (METERS_TO_FEET * FEET_TO_MILES)
            val delta = milesToMeters / 2
            ((targetDistance(expression.rvalue as Double) - delta) <= trip.distance!!) && ((targetDistance(
                expression.rvalue
            ) + delta) > trip.distance)
        }

        "greater than" -> trip.distance!! > expression.rvalue as Double
        "less than" -> trip.distance!! < expression.rvalue as Double
        "between" -> trip.distance!! >= targetDistance((expression.rvalue as List<*>)[0] as Double) &&
                trip.distance <= targetDistance(expression.rvalue[1] as Double)

        else -> false
    }
}

fun parseDate(rvalue: String): Any {
    val dateFormats = listOf(
        dateFormatPattenDob,
        "M/d/yyyy",
        "M-d-yyyy",
        "M d yyyy",
        "MMM d yyyy",
        "d MMM yyyy",
        "MMMM d yyyy",
        "d MMMM yyyy"
    )
    val monthYearFormats = listOf(
        "M yyyy",
        "M-yyyy",
        "M/yyyy",
        "MMM yyyy",
        "MMM-yyyy",
        "MMMM yyyy",
        "yyyy M",
        "yyyy-M",
        "yyyy/M",
        "yyyy MMM"
    )
    val monthFormats = listOf(
        "MMM",
        "MMMM"
    )
    val yearFormats = listOf(
        "yyyy"
    )
    dateFormats.forEach {
        try {
            val start = SimpleDateFormat(it, Locale.US).parse(rvalue)!!.toInstant()
            return listOf(
                start.atZone(ZoneId.systemDefault())
                    .truncatedTo(ChronoUnit.DAYS).toInstant(),
                start.atZone(ZoneId.systemDefault())
                    .truncatedTo(ChronoUnit.DAYS).plusDays(1).toInstant()
            )
        } catch (e: DateTimeParseException) {
            Log.d("parseDate", "$rvalue could not be parsed as date")
        } catch (e: ParseException) {
            Log.d("parseDate", "$rvalue could not be parsed as date")
        } catch (e: NullPointerException) {
            Log.d("parseDate", "$rvalue could not be parsed as date")
        }
    }
    monthYearFormats.forEach {
        try {
            val start = SimpleDateFormat(it, Locale.US).parse(rvalue)!!.toInstant()
            return listOf(
                start.atZone(ZoneId.systemDefault()).with(TemporalAdjusters.firstDayOfMonth())
                    .truncatedTo(ChronoUnit.DAYS).toInstant(),
                start.atZone(ZoneId.systemDefault()).with(TemporalAdjusters.lastDayOfMonth())
                    .truncatedTo(ChronoUnit.DAYS).toInstant()
            )
        } catch (e: DateTimeParseException) {
            Log.d("parseDate", "$rvalue could not be parsed as date")
        } catch (e: ParseException) {
            Log.d("parseDate", "$rvalue could not be parsed as date")
        } catch (e: NullPointerException) {
            Log.d("parseDate", "$rvalue could not be parsed as date")
        }
    }
    monthFormats.forEach {
        try {
            val month = SimpleDateFormat(it, Locale.US).parse(rvalue)!!.toInstant()
            val start = Instant.now().atZone(ZoneId.systemDefault())
                .withMonth(month.atZone(ZoneId.systemDefault()).month.value)
            return listOf(
                start.with(TemporalAdjusters.firstDayOfMonth()).truncatedTo(ChronoUnit.DAYS)
                    .toInstant(),
                start.with(TemporalAdjusters.lastDayOfMonth()).truncatedTo(ChronoUnit.DAYS)
                    .toInstant()
            )
        } catch (e: DateTimeParseException) {
            Log.d("parseDate", "$rvalue could not be parsed as date")
        } catch (e: ParseException) {
            Log.d("parseDate", "$rvalue could not be parsed as date")
        } catch (e: NullPointerException) {
            Log.d("parseDate", "$rvalue could not be parsed as date")
        }
    }
    yearFormats.forEach {
        try {
            val start = SimpleDateFormat(it, Locale.US).parse(rvalue)!!
                .toInstant()
            if (start.isBefore(
                    LocalDate.of(1970, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
                )
            ) throw DateTimeParseException("Year before 1970", rvalue, 0)
            return listOf(
                start.atZone(ZoneId.systemDefault()).truncatedTo(
                    ChronoUnit.DAYS
                ).toInstant(),
                start.atZone(ZoneId.systemDefault()).with(TemporalAdjusters.lastDayOfYear())
                    .truncatedTo(ChronoUnit.DAYS).toInstant()
            )
        } catch (e: DateTimeParseException) {
            Log.d("parseDate", "$rvalue could not be parsed as date")
        } catch (e: ParseException) {
            Log.d("parseDate", "$rvalue could not be parsed as date")
        } catch (e: NullPointerException) {
            Log.d("parseDate", "$rvalue could not be parsed as date")
        }
    }
    if (!devBuild) {
        FirebaseCrashlytics.getInstance()
            .recordException(ParseException("$rvalue is not a recognized date string", 0))
    }
    throw ParseException("$rvalue is not a recognized date string", 0)
}

fun parseRvalue(regexMatchGroups: MatchGroupCollection): Any =
    when (regexMatchGroups["operator"]!!.value.lowercase()) {
        "between" -> Regex("""(?<lower>\S+) and (?<upper>\S+)""")
            .find(regexMatchGroups["rvalue"]!!.value)?.groups?.let {
                arrayOf(it["lower"]!!.value, it["upper"]!!.value)
            }

        else -> Regex("""^['"]?(?<string>.*?)['"]?$""")
            .find(regexMatchGroups["rvalue"]!!.value)?.groups?.let {
                arrayOf(it["string"]!!.value)
            }
    }!!.map { rvalue ->
        when (regexMatchGroups["lvalue"]!!.value.lowercase()) {
            "distance" -> distanceToMeters(rvalue.toDouble(), "1")
            "date" -> parseDate(rvalue)

            else -> rvalue
        }
    }.let { typedArray ->
        when (typedArray.size) {
            1 -> typedArray[0]
            else -> typedArray
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
        parseRvalue(regexMatchGroups),
        regexMatchGroups["junction"]?.value,
    )
}