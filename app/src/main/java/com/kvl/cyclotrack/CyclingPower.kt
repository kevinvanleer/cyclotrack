package com.kvl.cyclotrack

import android.util.Log

data class PowerData(
    val instantaneousPower: Int?,
    val pedalPowerBalance: Float? = null,
    val pedalPowerBalanceReference: PedalPowerBalanceReference? = null,
    val accumulatedTorque: Float? = null,
    val accumulatedTorqueSource: AccumulatedTorqueSource? = null,
    val cumulativeWheelRevolutions: Int? = null,
    val lastWheelEventTime: Int? = null,
    val cumulativeCrankRevolutions: Int? = null,
    val lastCrankEventTime: Int? = null,
    val maximumForceMagnitude: Int? = null,
    val minimumForceMagnitude: Int? = null,
    val maximumTorqueMagnitude: Float? = null,
    val minimumTorqueMagnitude: Float? = null,
    val maximumAngle: Int? = null,
    val minimumAngle: Int? = null,
    val topDeadSpotAngle: Int? = null,
    val bottomDeadSpotAngle: Int? = null,
    val accumulatedEnergy: Int? = null,
    val offsetCompensation: Boolean? = null,
    val rpm: Float? = null,
    val wheelRpm: Float? = null,
    val emaPower: Int? = null,
    val averagePower: Int? = null,
    val batteryLevel: Byte? = null,
    val timestamp: Long? = null,
    val sampleCount: Long? = null,
)

enum class PedalPowerBalanceReference {
    UNKNOWN, LEFT
}

enum class AccumulatedTorqueSource {
    WHEEL_BASED, CRANK_BASED
}

data class WheelRevolutionData(val cumulativeRevolutions: UInt, val lastEventTime: UShort)
data class CrankRevolutionData(val cumulativeRevolutions: UShort, val lastEventTime: UShort)
data class ExtremeForce(val maximum: Short, val minimum: Short)
data class ExtremeTorque(val maximum: Float, val minimum: Float)
data class ExtremeAngles(val maximum: UShort, val minimum: UShort)

enum class CyclingPowerField(
    val bit: Int,
    val size: Int,
    val label: String,
    val convert: ((ByteArray) -> Any),
) {
    FLAGS(
        bit = -1, size = 2, label = "Flags",
        convert = { it.leUint16() }
    ),
    INST_POWER(
        bit = -1, size = 2, label = "Instantaneous Power (W)",
        convert = { it.leInt16Signed() }
    ),
    PEDAL_BALANCE(
        bit = 0, size = 1, label = "Pedal Power Balance (%)",
        convert = { it.leUint8().toInt() * 0.005f }
    ),
    ACCUM_TORQUE(
        bit = 2, size = 2, label = "Accumulated Torque (Nm)",
        convert = { it.leUint16().toInt() / 32.0f }
    ),
    WHEEL_REV(
        bit = 4, size = 6, label = "Wheel Revolution Data",
        convert = {
            WheelRevolutionData(
                cumulativeRevolutions = it.leUint32(0),
                lastEventTime = it.leUint16(4),
            )
        }
    ),
    CRANK_REV(
        bit = 5, size = 4, label = "Crank Revolution Data",
        convert = {
            CrankRevolutionData(
                cumulativeRevolutions = it.leUint16(0),
                lastEventTime = it.leUint16(2),
            )
        }
    ),
    EXTREME_FORCE(
        bit = 6, size = 4, label = "Extreme Force Magnitudes",
        convert = {
            ExtremeForce(
                maximum = it.leInt16Signed(0),
                minimum = it.leInt16Signed(2),
            )
        }
    ),
    EXTREME_TORQUE(
        bit = 7, size = 4, label = "Extreme Torque Magnitudes",
        convert = {
            ExtremeTorque(
                maximum = it.leUint16(0).toInt() / 32.0f,
                minimum = it.leUint16(2).toInt() / 32.0f,
            )
        }
    ),
    EXTREME_ANGLES(
        bit = 8, size = 3, label = "Extreme Angles",
        convert = {
            val b0 = it.leUint8(0).toInt();
            val b1 = it.leUint8(1).toInt();
            val b2 = it.leUint8(2).toInt();
            ExtremeAngles(
                maximum = (b0 or ((b1 and 0x0F) shl 8)).toUShort(),
                minimum = ((b1 shr 4) or (b2 shl 4)).toUShort(),
            )
        }
    ),
    TOP_DEAD_SPOT(
        bit = 9, size = 2, label = "Top Dead Spot Angle (deg)",
        convert = { it.leUint16() }
    ),
    BOT_DEAD_SPOT(
        bit = 10, size = 2, label = "Bottom Dead Spot Angle (deg)",
        convert = { it.leUint16() }
    ),
    ACCUM_ENERGY(
        bit = 11, size = 2, label = "Accumulated Energy (kJ)",
        convert = { it.leUint16() }
    ),
    OFFSET_COMP(
        bit = 12, size = 0, label = "Offset Compensation Indicator",
        convert = { true }
    ),
}

private fun ByteArray.leUint8(offset: Int = 0): UByte =
    (this[offset].toInt() and 0xFF).toUByte()

private fun ByteArray.leUint16(offset: Int = 0): UShort =
    ((this[offset + 1].toInt() and 0xFF shl 8) or (this[offset].toInt() and 0xFF)).toUShort()

private fun ByteArray.leUint32(offset: Int = 0): UInt =
    ((leUint16(offset + 2).toUInt() shl 16) or leUint16(offset).toUInt())

private fun ByteArray.leInt16Signed(offset: Int = 0): Short =
    ((this[offset + 1].toInt() and 0xFF shl 8) or (this[offset].toInt() and 0xFF)).toShort()

val messageOrder = listOf(
    CyclingPowerField.INST_POWER,
    CyclingPowerField.PEDAL_BALANCE,
    CyclingPowerField.ACCUM_TORQUE,
    CyclingPowerField.WHEEL_REV,
    CyclingPowerField.CRANK_REV,
    CyclingPowerField.EXTREME_FORCE,
    CyclingPowerField.EXTREME_TORQUE,
    CyclingPowerField.EXTREME_ANGLES,
    CyclingPowerField.TOP_DEAD_SPOT,
    CyclingPowerField.BOT_DEAD_SPOT,
    CyclingPowerField.ACCUM_ENERGY,
)

fun parseCyclingPower(bytes: ByteArray, prev: PowerData): PowerData {
    var remaining = bytes

    fun parse(field: CyclingPowerField, flags: Int): Pair<CyclingPowerField, Any?> {
        Log.v("CyclingPower", "flags: ${flags.toHexString(HexFormat.Default)}")
        Log.v("CyclingPower", "shift: ${(flags shr field.bit).toHexString(HexFormat.Default)}")
        val present =
            CyclingPowerField.FLAGS == field || CyclingPowerField.INST_POWER == field || ((flags shr field.bit) and 1) == 1
        Log.v("CyclingPower", "${field.label}: $present")
        if (!present) return Pair(field, null)

        Log.v("CyclingPower", "remaining: ${remaining.toHexString(HexFormat.Default)}")
        val value = field.convert(remaining)
        remaining = remaining.sliceArray(field.size until remaining.size)
        return Pair(field, value)
    }

    val flags = (parse(CyclingPowerField.FLAGS, 0).second as UShort).toInt()
    val parsed = messageOrder.map { field -> parse(field, flags) }
    Log.v("CyclingPower", parsed.toString())
    var idx = 0
    val instantaneousPower = (parsed[idx++].second as? Short)?.toInt()
    val pedalPowerBalance = parsed[idx++].second as? Float
    val accumulatedTorque = parsed[idx++].second as? Float
    val wheelRevolutions = (parsed[idx++].second as? WheelRevolutionData)
    val crankRevolutions = (parsed[idx++].second as? CrankRevolutionData)
    val extremeForceMagnitudes = (parsed[idx++].second as? ExtremeForce)
    val extremeTorqueMagnitudes = (parsed[idx++].second as? ExtremeTorque)
    val extremeAngles = (parsed[idx++].second as? ExtremeAngles)
    val topDeadSpotAngle = parsed[idx++].second as? UShort
    val bottomDeadSpotAngle = parsed[idx++].second as? UShort
    val accumulatedEnergy = parsed[idx].second as? Int
    return PowerData(
        instantaneousPower = instantaneousPower,
        pedalPowerBalance = pedalPowerBalance,
        pedalPowerBalanceReference = if ((flags shr 1 and 1) == 1) PedalPowerBalanceReference.LEFT else PedalPowerBalanceReference.UNKNOWN,
        accumulatedTorque = accumulatedTorque,
        accumulatedTorqueSource = if ((flags shr 3 and 1) == 1) AccumulatedTorqueSource.CRANK_BASED else AccumulatedTorqueSource.WHEEL_BASED,
        cumulativeWheelRevolutions = wheelRevolutions?.cumulativeRevolutions?.toInt(),
        lastWheelEventTime = wheelRevolutions?.lastEventTime?.toInt(),
        cumulativeCrankRevolutions = crankRevolutions?.cumulativeRevolutions?.toInt(),
        lastCrankEventTime = crankRevolutions?.lastEventTime?.toInt(),
        maximumForceMagnitude = extremeForceMagnitudes?.maximum?.toInt(),
        minimumForceMagnitude = extremeForceMagnitudes?.minimum?.toInt(),
        maximumTorqueMagnitude = extremeTorqueMagnitudes?.maximum,
        minimumTorqueMagnitude = extremeTorqueMagnitudes?.minimum,
        maximumAngle = extremeAngles?.maximum?.toInt(),
        minimumAngle = extremeAngles?.minimum?.toInt(),
        topDeadSpotAngle = topDeadSpotAngle?.toInt(),
        bottomDeadSpotAngle = bottomDeadSpotAngle?.toInt(),
        accumulatedEnergy = accumulatedEnergy,
        batteryLevel = prev.batteryLevel,
        rpm = getRpm(
            revLast = prev.cumulativeCrankRevolutions
                ?: crankRevolutions?.cumulativeRevolutions?.toInt() ?: 0,
            timeLast = prev.lastCrankEventTime ?: crankRevolutions?.lastEventTime?.toInt() ?: 0,
            rev = crankRevolutions?.cumulativeRevolutions?.toInt() ?: 0,
            time = crankRevolutions?.lastEventTime?.toInt() ?: 0,
        ),
        wheelRpm = getRpm(
            revLast = prev.cumulativeWheelRevolutions
                ?: wheelRevolutions?.cumulativeRevolutions?.toInt() ?: 0,
            timeLast = prev.lastWheelEventTime ?: 0,
            rev = wheelRevolutions?.cumulativeRevolutions?.toInt() ?: 0,
            time = wheelRevolutions?.lastEventTime?.toInt() ?: prev.lastWheelEventTime ?: 0,
        ),
        offsetCompensation = ((flags shr 12 and 1) == 1),
        timestamp = System.currentTimeMillis(),
    )
}
