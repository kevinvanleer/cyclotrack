package com.kvl.cyclotrack.util

import kotlin.math.abs

const val METERS_TO_FEET = 3.28084
const val FEET_TO_METERS = 1 / METERS_TO_FEET
const val METERS_TO_KM = 0.001
const val METERS_TO_MM = 1000.0
const val FEET_TO_MILES = 1.0 / 5280
const val HOURS_TO_SECONDS = 3600.0
const val SECONDS_TO_HOURS = 1.0 / HOURS_TO_SECONDS
const val INCHES_TO_FEET = 1 / 12.0
const val FEET_TO_INCHES = 12.0
const val KELVIN_TO_CELSIUS = 273.15
const val MILES_TO_FEET = 1 / FEET_TO_MILES

const val SECONDS_PER_HOUR = HOURS_TO_SECONDS
const val METERS_PER_MILE = MILES_TO_FEET * FEET_TO_METERS
const val MILES_TO_METERS = METERS_PER_MILE
const val METERS_TO_MILES = 1 / MILES_TO_METERS
const val MILES_PER_HR_TO_METERS_PER_SEC = HOURS_TO_SECONDS * MILES_TO_METERS

object MetricPrefix {
    const val NANO: Double = 1e-9
    const val MICRO: Double = 1e-6
    const val MILLI: Double = 1e-3
    const val CENTI: Double = 1e-2
    const val DECI: Double = 1e-1
    const val BASE: Double = 1.0
    const val DECA: Double = 1e1
    const val HECTO: Double = 1e2
    const val KILO: Double = 1e3
    const val MEGA: Double = 1e6
    const val GIGA: Double = 1e9
}

//USCU = United States Customary Units
object LengthConversions {
    const val YARD: Double = 0.9144
    const val INCH: Double = YARD / 36.0
    const val FOOT: Double = YARD / 3.0
    const val MILE: Double = 1760.0 * YARD
    const val LEAGUE: Double = 5280.0 * YARD
    const val METER: Double = 1.0
}

object MetersPerSecond : Speed(Meter, Second)
object MilesPerHour : Speed(Mile, Hour)
object KilometersPerHour : Speed(Kilo(Meter), Hour)

open class Speed(override val numerator: Unit, override val denominator: Unit) : Rate {
    override val conversionFactor: Double
        get() = numerator.conversionFactor / denominator.conversionFactor
    override val baseUnit: Rate
        get() = Speed(numerator.baseUnit, denominator.baseUnit)
}

interface Rate : Unit {
    val numerator: Unit
    val denominator: Unit
}

class Quantity(private val quantity: Double, private val units: Unit) {
    val value
        get() = quantity
    val unit
        get() = units

    fun convertTo(destUnits: Unit) =
        Quantity(
            quantity * units.conversionFactor / destUnits.conversionFactor,
            destUnits
        )

    operator fun plus(other: Quantity): Quantity {
        if (units.baseUnit::class != other.units.baseUnit::class) {
            throw Exception()
        }
        return Quantity(quantity + other.convertTo(units).value, units)
    }

    operator fun minus(other: Quantity): Quantity {
        if (units.baseUnit::class != other.units.baseUnit::class) {
            throw Exception()
        }
        return Quantity(quantity - other.convertTo(units).value, units)
    }

    operator fun times(other: Quantity): Quantity {
        if (units.baseUnit::class != other.units.baseUnit::class) {
            throw Exception()
        }
        return Quantity(quantity * other.convertTo(units).value, units)
    }

    operator fun div(other: Quantity): Quantity {
        if (units.baseUnit::class != other.units.baseUnit::class) {
            throw Exception()
        }
        return Quantity(quantity / other.convertTo(units).value, units)
    }

    override operator fun equals(other: Any?): Boolean {
        if (other !is Quantity || units.baseUnit::class != other.units.baseUnit::class) {
            throw Exception()
        }
        return abs(this.minus(other).value) < 1e-12
    }

    operator fun compareTo(other: Quantity): Int {
        if (units.baseUnit::class != other.units.baseUnit::class) {
            throw Exception()
        }
        return when (abs(this.minus(other).value) < 1e-12) {
            true -> 0
            else -> quantity.compareTo(other.convertTo(units).value)
        }
    }

    override fun hashCode(): Int {
        var result = quantity.hashCode()
        result = 31 * result + units.hashCode()
        return result
    }
}

open class Centi(val base: Unit) : Unit {
    override val conversionFactor: Double
        get() = base.conversionFactor * MetricPrefix.CENTI
    override val baseUnit: Unit
        get() = base.baseUnit

}

open class Kilo(val base: Unit) : Unit {
    override val conversionFactor: Double
        get() = base.conversionFactor * MetricPrefix.KILO
    override val baseUnit: Unit
        get() = base.baseUnit

}

object Kilometer : Kilo(Meter)
object Centimeter : Centi(Meter)
object Meter : Length() {
    override val baseUnit: Unit = Meter
}

object Yard : Length() {
    override val conversionFactor: Double =
        LengthConversions.YARD
}

object Mile : Length() {
    override val conversionFactor: Double =
        LengthConversions.MILE
}

object Foot : Length() {
    override val conversionFactor: Double =
        LengthConversions.FOOT
}

object Inch : Length() {
    override val conversionFactor: Double =
        LengthConversions.INCH
}

open class Length : Unit {
    override val conversionFactor: Double = 1.0
    override val baseUnit: Unit = Meter
}

object Year : Time() {
    override val conversionFactor: Double = TimeConversions.YEAR
}

object Week : Time() {
    override val conversionFactor: Double = TimeConversions.WEEK
}

object Day : Time() {
    override val conversionFactor: Double = TimeConversions.DAY
}

object Hour : Time() {
    override val conversionFactor: Double = TimeConversions.HOUR
}

object Minute : Time() {
    override val conversionFactor: Double = TimeConversions.MINUTE
    override fun toString(): String = "minutes"
}

object Second : Time() {
    override val baseUnit: Unit = Second
    override fun toString(): String = "second"
}

open class Time : Unit {
    override val conversionFactor: Double = TimeConversions.SECOND
    override val baseUnit: Unit = Second
}

interface Unit {
    val conversionFactor: Double
    val baseUnit: Unit
}

object TimeConversions {
    const val SECOND: Double = 1.0
    const val MINUTE: Double = 60.0
    const val HOUR: Double = 3600.0
    const val DAY: Double = HOUR * 24.0
    const val WEEK: Double = DAY * 7.0
    const val YEAR: Double = DAY * 365.25
}

fun kelvinToCelsius(kelvin: Double) = kelvin - KELVIN_TO_CELSIUS
fun kelvinToFahrenheit(kelvin: Double) = kelvinToCelsius(kelvin) * 9.0 / 5.0 + 32.0

fun unitsToSystem(units: String?): String {
    return when (units) {
        "kilometers", "km", "cm", "C", "km/h" -> "2"
        "miles", "mile", "mi", "F", "mph", "m/h", "in", "inch", "inches", "ft", "feet" -> "1"
        else -> "0"
    }
}

fun distanceToMeters(distance: Double, measurementSystem: String) =
    when (measurementSystem) {
        "miles", "mile", "mi", "1" -> Quantity(distance, Mile).convertTo(Meter).value
        "kilometers", "km", "2" -> Quantity(distance, Kilometer).convertTo(Meter).value
        else -> distance
    }
