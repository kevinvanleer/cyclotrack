/*
Adapted from:
https://android.googlesource.com/platform/frameworks/base/+/db29556/core/java/android/bluetooth/BluetoothGattCharacteristic.java

These methods were removed from Android 33 SDK
 */
package com.kvl.cyclotrack.util

import android.bluetooth.BluetoothGattCharacteristic.*
import kotlin.math.pow

fun ByteArray.getIntValue(formatType: Int, offset: Int): Int? {
    val mValue = this
    if (offset + getTypeLen(formatType) > mValue.size) return null
    when (formatType) {
        FORMAT_UINT8 -> return unsignedByteToInt(mValue[offset])
        FORMAT_UINT16 -> return unsignedBytesToInt(mValue[offset], mValue[offset + 1])
        FORMAT_UINT32 -> return unsignedBytesToInt(
            mValue[offset], mValue[offset + 1],
            mValue[offset + 2], mValue[offset + 3]
        )
        FORMAT_SINT8 -> return unsignedToSigned(unsignedByteToInt(mValue[offset]), 8)
        FORMAT_SINT16 -> return unsignedToSigned(
            unsignedBytesToInt(
                mValue[offset],
                mValue[offset + 1]
            ), 16
        )
        FORMAT_SINT32 -> return unsignedToSigned(
            unsignedBytesToInt(
                mValue[offset],
                mValue[offset + 1], mValue[offset + 2], mValue[offset + 3]
            ), 32
        )
    }
    return null
}

fun ByteArray.getFloatValue(formatType: Int, offset: Int): Float? {
    val mValue = this
    if (offset + getTypeLen(formatType) > mValue.size) return null
    when (formatType) {
        FORMAT_SFLOAT -> return bytesToFloat(mValue[offset], mValue[offset + 1])
        FORMAT_FLOAT -> return bytesToFloat(
            mValue[offset], mValue[offset + 1],
            mValue[offset + 2], mValue[offset + 3]
        )
    }
    return null
}

fun ByteArray.getStringValue(offset: Int): String? {
    val mValue = this
    if (offset > mValue.size) return null
    val strBytes = ByteArray(mValue.size - offset)
    for (i in 0 until mValue.size - offset) strBytes[i] = mValue[offset + i]
    return String(strBytes)
}

private fun getTypeLen(formatType: Int): Int {
    return formatType and 0xF
}

private fun unsignedByteToInt(b: Byte): Int {
    return b.toInt() and 0xFF
}

private fun unsignedBytesToInt(b0: Byte, b1: Byte): Int {
    return unsignedByteToInt(b0) + (unsignedByteToInt(b1) shl 8)
}

private fun unsignedBytesToInt(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
    return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) shl 8)
            + (unsignedByteToInt(b2) shl 16) + (unsignedByteToInt(b3) shl 24))
}

private fun bytesToFloat(b0: Byte, b1: Byte): Float {
    val mantissa = unsignedToSigned(
        unsignedByteToInt(b0)
                + (unsignedByteToInt(b1) and 0x0F shl 8), 12
    )
    val exponent = unsignedToSigned(unsignedByteToInt(b1) shr 4, 4)
    return (mantissa * 10.0.pow(exponent.toDouble())).toFloat()
}

private fun bytesToFloat(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Float {
    val mantissa = unsignedToSigned(
        unsignedByteToInt(b0)
                + (unsignedByteToInt(b1) shl 8)
                + (unsignedByteToInt(b2) shl 16), 24
    )
    return (mantissa * 10.0.pow(b3.toDouble())).toFloat()
}

private fun unsignedToSigned(unsigned: Int, size: Int): Int {
    var signed = unsigned
    if (signed and (1 shl size - 1) != 0) {
        signed = -1 * ((1 shl size - 1) - (signed and (1 shl size - 1) - 1))
    }
    return signed
}

private fun intToSignedBits(i: Int, size: Int): Int {
    var signedBits = i
    if (signedBits < 0) {
        signedBits = (1 shl size - 1) + (signedBits and (1 shl size - 1) - 1)
    }
    return signedBits
}
