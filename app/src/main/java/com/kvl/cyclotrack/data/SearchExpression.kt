package com.kvl.cyclotrack.data

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kvl.cyclotrack.FeatureFlags.Companion.devBuild
import com.kvl.cyclotrack.Trip
import com.kvl.cyclotrack.util.Kilogram
import com.kvl.cyclotrack.util.Length
import com.kvl.cyclotrack.util.Mass
import com.kvl.cyclotrack.util.Meter
import com.kvl.cyclotrack.util.MetersPerSecond
import com.kvl.cyclotrack.util.Mile
import com.kvl.cyclotrack.util.MilesPerHour
import com.kvl.cyclotrack.util.Quantity
import com.kvl.cyclotrack.util.Speed
import com.kvl.cyclotrack.util.dateFormatPattenDob
import com.kvl.cyclotrack.util.quantifyDistance
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
import com.kvl.cyclotrack.util.Unit as Units

val numberStringRegex = Regex("""^\s*(?<value>\d+.?\d+?)( (?<units>\S+)\s*)?$""")
val expressionRegex =
    Regex("""(?<junction>and|or)?\s?(?<negation>not)?\s?(?<lvalue>distance|date|speed|weight|mass|bike|text) (?<operator>contains|is|equals|less than|greater than|before|after|between) (?<rvalue>\".*?\"|\'.*?\'|[\d\-/]+ (and|or) [\d\-/]+|\S+)\s?""")


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
                it.groups["units"]?.value?.takeUnless { s -> s.isEmpty() }?.let { unitString ->
                    Units.fromString(unitString)?.let { detectedUnit ->
                        SearchExpression(
                            lvalue = when (detectedUnit.baseUnit) {
                                is Speed ->
                                    "speed"

                                is Mass ->
                                    "mass"

                                is Length ->
                                    "distance"

                                else ->
                                    "text"
                            },
                            operator = "is",
                            rvalue = Quantity(
                                it.groups["value"]!!.value.toDouble(),
                                detectedUnit
                            ),
                        )
                    }
                } ?: SearchExpression(
                    lvalue = "distance",
                    operator = "is",
                    rvalue = quantifyDistance(
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

    return expressionRegex.findAll(searchString)
        .map {
            SearchExpression(it.groups, measurementSystem)
        }.toList().takeUnless { it.isNullOrEmpty() } ?: listOf(
        SearchExpression(
            lvalue = "text",
            operator = "contains",
            rvalue = searchString
        )
    )
}

fun compareTextExpression(trip: Trip, expression: SearchExpression): Boolean =
    when (expression.operator) {
        "contains" -> trip.name?.lowercase()
            ?.contains(
                expression.rvalue.toString().lowercase()
            ) == true || trip.notes?.lowercase()
            ?.contains(
                expression.rvalue.toString().lowercase()
            ) == true

        else -> throw ParseException("Invalid text search operator", 6)
    }

fun tripPassesExpression(trip: Trip, searchExpressions: List<SearchExpression>): Boolean =
    searchExpressions.fold(false) { result, expression ->
        when (expression.lvalue.lowercase()) {
            "distance" ->
                compareDistanceExpression(trip, expression)

            "speed" -> compareSpeedExpression(trip, expression)

            "mass", "weight" -> compareMassExpression(trip, expression)

            "date" -> compareDateExpression(trip, expression)
            "text" -> compareTextExpression(trip, expression)

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

fun compareDistanceExpressionOld(
    trip: Trip,
    expression: SearchExpression,
): Boolean {
    return when (expression.operator.lowercase()) {
        "is", "equals" -> {
            val delta = Quantity(0.5, Mile).convertTo(Meter).value
            (((expression.rvalue as Double) - delta) <= trip.distance!!) && (((
                    expression.rvalue
                    ) + delta) > trip.distance)
        }

        "greater than" -> trip.distance!! > expression.rvalue as Double
        "less than" -> trip.distance!! < expression.rvalue as Double
        "between" -> trip.distance!! >= ((expression.rvalue as List<*>)[0] as Double) &&
                trip.distance <= (expression.rvalue[1] as Double)

        else -> false
    }
}

fun compareDistanceExpression(
    trip: Trip,
    expression: SearchExpression,
): Boolean = trip.distance?.let { distance ->
    Quantity(distance, Meter).let { quantity ->
        when (expression.operator.lowercase()) {
            "is", "equals" -> {
                val delta = Quantity(0.5, (expression.rvalue as Quantity).unit)
                (((expression.rvalue as Quantity) - delta) <= quantity) && (((
                        expression.rvalue
                        ) + delta) > quantity)
            }

            "greater than" -> quantity > expression.rvalue as Quantity
            "less than" -> quantity < expression.rvalue as Quantity
            "between" -> quantity >= ((expression.rvalue as List<*>)[0] as Quantity) &&
                    quantity <= (expression.rvalue[1] as Quantity)

            else -> false
        }
    }
} ?: false

fun compareMassExpression(
    trip: Trip,
    expression: SearchExpression,
): Boolean = trip.userWeight?.let { userWeight ->
    Quantity(userWeight.toDouble(), Kilogram).let { mass ->
        when (expression.operator.lowercase()) {
            "is", "equals" -> {
                val delta = Quantity(0.5, (expression.rvalue as Quantity).unit)
                (((expression.rvalue as Quantity) - delta) <= mass) && (((
                        expression.rvalue
                        ) + delta) > mass)
            }

            "greater than" -> mass > expression.rvalue as Quantity
            "less than" -> mass < expression.rvalue as Quantity
            "between" -> mass >= ((expression.rvalue as List<*>)[0] as Quantity) &&
                    mass <= (expression.rvalue[1] as Quantity)

            else -> false
        }
    }
} ?: false

fun compareSpeedExpressionOld(
    trip: Trip,
    expression: SearchExpression,
): Boolean {
    return trip.averageSpeed?.toDouble()?.let { speed ->
        when (expression.operator.lowercase()) {
            "is", "equals" -> {
                val delta = Quantity(0.1, MilesPerHour).convertTo(MetersPerSecond).value
                (((expression.rvalue as Double) - delta) <= speed) && (((
                        expression.rvalue
                        ) + delta) > speed)
            }

            "greater than" -> speed > expression.rvalue as Double
            "less than" -> speed < expression.rvalue as Double
            "between" -> speed >= ((expression.rvalue as List<*>)[0] as Double) &&
                    speed <= (expression.rvalue[1] as Double)

            else -> false
        }
    } ?: false
}

fun compareSpeedExpression(
    trip: Trip,
    expression: SearchExpression,
): Boolean = trip.averageSpeed?.let { speed ->
    Quantity(speed.toDouble(), MetersPerSecond).let { quantity ->
        when (expression.operator.lowercase()) {
            "is", "equals" -> {
                val delta = Quantity(0.5, (expression.rvalue as Quantity).unit)
                (((expression.rvalue as Quantity) - delta) <= quantity) && (((
                        expression.rvalue
                        ) + delta) > quantity)
            }

            "greater than" -> quantity > expression.rvalue as Quantity
            "less than" -> quantity < expression.rvalue as Quantity
            "between" -> quantity >= ((expression.rvalue as List<*>)[0] as Quantity) &&
                    quantity <= (expression.rvalue[1] as Quantity)

            else -> false
        }
    }
} ?: false

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

fun parseRvalue(regexMatchGroups: MatchGroupCollection, measurementSystem: String = "0"): Any =
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
            //"distance" -> normalizeDistance(rvalue.toDouble(), measurementSystem)
            "distance" -> Quantity(
                rvalue.toDouble(),
                Length.fromMeasurementSystem(measurementSystem)
            )

            "speed" -> Quantity(
                rvalue.toDouble(),
                Speed.fromMeasurementSystem(measurementSystem)
            )

            "weight", "mass" -> Quantity(
                rvalue.toDouble(),
                Mass.fromMeasurementSystem(measurementSystem)
            )

            "date" -> parseDate(rvalue)

            "bike" -> NotImplementedError()

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

    constructor(regexMatchGroups: MatchGroupCollection, measurementSystem: String) : this(
        !regexMatchGroups["negation"]?.value.isNullOrEmpty(),
        regexMatchGroups["lvalue"]!!.value,
        regexMatchGroups["operator"]!!.value,
        parseRvalue(regexMatchGroups, measurementSystem),
        regexMatchGroups["junction"]?.value,
    )
}