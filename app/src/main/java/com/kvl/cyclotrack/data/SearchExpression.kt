package com.kvl.cyclotrack.data

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.FEET_TO_MILES
import com.kvl.cyclotrack.METERS_TO_FEET
import com.kvl.cyclotrack.Trip
import com.kvl.cyclotrack.util.dateFormatPattenDob
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

val expressionRegex =
    Regex("""(?<junction>and|or)?\s?(?<negation>not)?\s?(?<lvalue>distance|date|text) (?<operator>contains|is|equals|less than|greater than|before|after|between) (?<rvalue>\".*?\"|\'.*?\'|[\d\-/]+ (and|or) [\d\-/]+|\S+)\s?""")

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
            .isAfter(expression.rvalue as Instant)

        "less than", "before" -> Instant.ofEpochMilli(trip.timestamp)
            .isBefore(expression.rvalue as Instant)

        "is" -> Instant.ofEpochMilli(trip.timestamp)
            .isAfter((expression.rvalue as List<*>)[0] as Instant)
                && Instant.ofEpochMilli(trip.timestamp)
            .isBefore((expression.rvalue)[1] as Instant)

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
            val start = SimpleDateFormat(it, Locale.US).parse(rvalue)!!.toInstant()
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
    FirebaseCrashlytics.getInstance()
        .recordException(ParseException("$rvalue is not a recognized date string", 0))
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
            "distance" -> rvalue.toDouble()
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