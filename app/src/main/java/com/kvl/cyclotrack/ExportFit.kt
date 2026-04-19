package com.kvl.cyclotrack

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.Settings
import com.garmin.fit.ActivityMesg
import com.garmin.fit.DateTime
import com.garmin.fit.DeveloperDataIdMesg
import com.garmin.fit.DeviceIndex
import com.garmin.fit.DeviceInfoMesg
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.File
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.Mesg
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import com.kvl.cyclotrack.util.getFileName
import com.kvl.cyclotrack.util.kelvinToCelsius
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Date
import java.util.TimeZone
import kotlin.math.pow
import kotlin.math.roundToInt

val cyclotrackFitAppId = "comkvlcyclotrack".toByteArray()

private const val FIT_EPOCH_SECONDS = 631_065_600L

private object LocalNum {
    const val GPS = 0
    const val HR = 1
    const val SPEED = 2
    const val CADENCE = 3
    const val POWER = 4
    const val EVENT = 5
}

internal fun fitSec(ms: Long): Long = ms / 1000 - FIT_EPOCH_SECONDS

internal fun pauseIntervals(timeStates: Array<TimeState>?): List<LongRange> {
    if (timeStates.isNullOrEmpty()) return emptyList()
    val sorted = timeStates.sortedBy { it.timestamp }
    val result = mutableListOf<LongRange>()
    var pauseStart: Long? = null
    for (ts in sorted) {
        when (ts.state) {
            TimeStateEnum.PAUSE ->
                if (pauseStart == null) pauseStart = fitSec(ts.timestamp)
            TimeStateEnum.RESUME, TimeStateEnum.START ->
                pauseStart?.let {
                    result.add(it until fitSec(ts.timestamp))
                    pauseStart = null
                }
            TimeStateEnum.STOP -> pauseStart = null
        }
    }
    return result
}

internal fun <T> bySecMean(
    samples: List<T>?,
    sec: (T) -> Long,
    value: (T) -> Double,
): Map<Long, Double> =
    samples?.groupBy(sec)
        ?.mapValues { (_, group) -> group.map(value).average() }
        ?: emptyMap()

internal fun interpolateGaps(
    samplesBySec: Map<Long, Double>,
    pausedSecs: Set<Long>,
): List<Pair<Long, Double>> {
    if (samplesBySec.size < 2) return samplesBySec.toList()
    val sorted = samplesBySec.toSortedMap()
    val secs = sorted.keys.toList()
    val out = mutableListOf<Pair<Long, Double>>()
    for (i in secs.indices) {
        val aSec = secs[i]
        val aVal = sorted.getValue(aSec)
        out.add(aSec to aVal)
        if (i == secs.lastIndex) break
        val bSec = secs[i + 1]
        val bVal = sorted.getValue(bSec)
        for (sec in (aSec + 1) until bSec) {
            if (sec in pausedSecs) continue
            val frac = (sec - aSec).toDouble() / (bSec - aSec)
            out.add(sec to aVal + (bVal - aVal) * frac)
        }
    }
    return out
}

private fun emitStream(
    samplesBySec: Map<Long, Double>,
    pausedSecs: Set<Long>,
    out: MutableList<RecordMesg>,
    localNum: Int,
    apply: RecordMesg.(Double) -> Unit,
) {
    interpolateGaps(samplesBySec, pausedSecs).forEach { (sec, value) ->
        out.add(RecordMesg().apply {
            this.localNum = localNum
            timestamp = DateTime(sec)
            apply(value)
        })
    }
}

private fun resolveCadenceSource(
    exportData: TripDetailsViewModel.ExportData,
): List<Pair<Long, Double>> {
    exportData.cadenceMeasurements?.takeIf { it.isNotEmpty() }?.let { csc ->
        return csc.mapNotNull { m -> m.rpm?.let { Pair(m.timestamp, it.toDouble()) } }
    }
    return exportData.powerMeasurements
        ?.mapNotNull { m -> m.rpm?.let { Pair(m.timestamp, it.toDouble()) } }
        ?: emptyList()
}

private fun makeTimerEvents(timeStates: Array<TimeState>?): List<EventMesg> {
    if (timeStates.isNullOrEmpty()) return emptyList()
    val sorted = timeStates.sortedBy { it.timestamp }
    // Drop a PAUSE immediately followed by STOP — STOP_ALL covers it
    val pruned = sorted.filterIndexed { i, ts ->
        !(ts.state == TimeStateEnum.PAUSE && sorted.getOrNull(i + 1)?.state == TimeStateEnum.STOP)
    }
    return pruned.map { ts ->
        EventMesg().apply {
            localNum = LocalNum.EVENT
            timestamp = DateTime(fitSec(ts.timestamp))
            event = Event.TIMER
            eventType = when (ts.state) {
                TimeStateEnum.START, TimeStateEnum.RESUME -> EventType.START
                TimeStateEnum.PAUSE -> EventType.STOP
                TimeStateEnum.STOP -> EventType.STOP_ALL
            }
        }
    }
}

fun makeFitMessages(
    appId: ByteArray,
    exportData: TripDetailsViewModel.ExportData,
): MutableList<Mesg> {
    val toSemicircles = 2.0.pow(31.0) / 180
    val messages: MutableList<Mesg> = ArrayList()

    val circumference = exportData.summary?.autoWheelCircumference
        ?: exportData.summary?.userWheelCircumference

    messages.add(DeveloperDataIdMesg().apply {
        for (i in appId.indices) {
            setApplicationId(i, appId[i])
        }
        applicationVersion = BuildConfig.VERSION_CODE.toLong()
        developerDataIndex = 0.toShort()
    })

    val timerEvents = makeTimerEvents(exportData.timeStates)
    val pausedSecs: Set<Long> = pauseIntervals(exportData.timeStates)
        .flatMapTo(HashSet()) { it.toList() }

    val records = mutableListOf<RecordMesg>()
    val emitGpsSpeed = exportData.speedMeasurements == null || circumference == null

    exportData.measurements
        ?.distinctBy { it.time / 1000 }
        ?.forEach {
            records.add(RecordMesg().apply {
                localNum = LocalNum.GPS
                timestamp = DateTime(fitSec(it.time))
                if (emitGpsSpeed) speed = it.speed
                altitude = it.altitude.toFloat()
                positionLat = (it.latitude * toSemicircles).toInt()
                positionLong = (it.longitude * toSemicircles).toInt()
                gpsAccuracy = it.accuracy.roundToInt().toShort()
            })
        }

    emitStream(
        bySecMean(exportData.heartRateMeasurements?.toList(), { fitSec(it.timestamp) }, { it.heartRate.toDouble() }),
        pausedSecs, records, LocalNum.HR,
    ) { heartRate = it.roundToInt().toShort() }

    if (circumference != null) {
        val rpmSamples = exportData.speedMeasurements
            ?.mapNotNull { m -> m.rpm?.let { Pair(m.timestamp, it.toDouble()) } }
        emitStream(
            bySecMean(rpmSamples, { fitSec(it.first) }, { it.second }),
            pausedSecs, records, LocalNum.SPEED,
        ) { speed = (it * circumference / 60).toFloat() }
    }

    emitStream(
        bySecMean(resolveCadenceSource(exportData), { fitSec(it.first) }, { it.second }),
        pausedSecs, records, LocalNum.CADENCE,
    ) { cadence = it.roundToInt().toShort() }

    emitStream(
        bySecMean(exportData.powerMeasurements?.toList(), { fitSec(it.timestamp) }, { it.instantaneousPower.toDouble() }),
        pausedSecs, records, LocalNum.POWER,
    ) { power = it.roundToInt() }

    val chronological: List<Mesg> =
        (records.map { Pair(it.timestamp.timestamp, it as Mesg) } +
                timerEvents.map { Pair(it.timestamp.timestamp, it as Mesg) })
            .sortedBy { it.first }
            .map { it.second }
    messages.addAll(chronological)

    exportData.splits?.forEachIndexed { idx, split ->
        messages.add(LapMesg().apply {
            messageIndex = idx
            startTime = DateTime(Date(split.timestamp - (split.duration * 1000).toLong()))
            timestamp = DateTime(Date(split.timestamp))
            totalElapsedTime = split.duration.toFloat()
            totalTimerTime = split.duration.toFloat()
            totalDistance = split.distance.toFloat()
        })
    }

    val timeStates = requireNotNull(exportData.timeStates) { "timeStates required for FIT export" }
    val sessionStartTime = requireNotNull(getStartTime(timeStates)) { "trip has no start time" }
    val sessionEndTime = requireNotNull(getEndTime(timeStates)) { "trip has no end time" }
    val splits = requireNotNull(exportData.splits) { "splits required for FIT export" }
    val summary = requireNotNull(exportData.summary) { "summary required for FIT export" }

    messages.add(SessionMesg().apply {
        messageIndex = 0
        timestamp = DateTime(Date(sessionEndTime))
        startTime = DateTime(Date(sessionStartTime))
        totalElapsedTime = ((sessionEndTime - sessionStartTime) / 1000.0).toFloat()
        totalTimerTime = accumulateActiveTime(timeStates).toFloat()
        sport = Sport.CYCLING
        subSport = SubSport.ROAD
        firstLapIndex = 0
        numLaps = splits.size
        exportData.weather?.map { it.temperature }?.takeIf { it.isNotEmpty() }?.average()?.let {
            avgTemperature = kelvinToCelsius(it).roundToInt().toByte()
        }
    })

    messages.add(ActivityMesg().apply {
        timestamp = DateTime(Date(summary.timestamp))
        numSessions = 1
        val timeZone: TimeZone = TimeZone.getDefault()
        val timezoneOffset = timeZone.rawOffset + timeZone.dstSavings
        localTimestamp = DateTime(Date(summary.timestamp + timezoneOffset)).timestamp
        totalTimerTime = accumulateActiveTime(timeStates).toFloat()
    })
    return messages
}

@SuppressLint("HardwareIds")
fun writeFitFile(
    context: Context,
    startTime: DateTime,
    file: java.io.File,
    messages: List<Mesg?>
) {
    val fileType = File.ACTIVITY
    val manufacturerId = Manufacturer.DEVELOPMENT.toShort()
    val productId = "CYCLOTRACK".hashCode()
    val softwareVersion = BuildConfig.VERSION_CODE.toFloat()
    val serialNumber =
        (Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "0").toULong(radix = 16).toLong()

    val fileIdMesg = FileIdMesg()
    fileIdMesg.type = fileType
    fileIdMesg.manufacturer = manufacturerId.toInt()
    fileIdMesg.product = productId
    fileIdMesg.timeCreated = startTime
    fileIdMesg.serialNumber = serialNumber

    val deviceInfoMesg = DeviceInfoMesg()
    deviceInfoMesg.deviceIndex = DeviceIndex.CREATOR
    deviceInfoMesg.manufacturer = Manufacturer.DEVELOPMENT
    deviceInfoMesg.product = productId
    deviceInfoMesg.productName = "Cyclotrack"
    deviceInfoMesg.serialNumber = serialNumber
    deviceInfoMesg.softwareVersion = softwareVersion
    deviceInfoMesg.timestamp = startTime

    val encode = FileEncoder(file, Fit.ProtocolVersion.V2_0)
    encode.write(fileIdMesg)
    encode.write(deviceInfoMesg)
    for (message in messages) {
        encode.write(message)
    }
    encode.close()
}

fun moveToDownloads(
    context: Context,
    source: java.io.File,
    destination: Uri,
) {
    context.contentResolver.openFileDescriptor(destination, "w")?.use {
        FileOutputStream(it.fileDescriptor).use { outStream ->
            FileInputStream(source).use { inStream ->
                inStream.copyTo(outStream)
                inStream.close()
            }
            outStream.close()
        }
    }
    source.delete()
}

fun exportRideToFit(
    context: Context,
    destination: Uri,
    exportData: TripDetailsViewModel.ExportData,
) {
    val privateAppFile = java.io.File(
        context.filesDir,
        getFileName(context.contentResolver, destination)!!
    )

    val messages: MutableList<Mesg> = makeFitMessages(cyclotrackFitAppId, exportData)
    writeFitFile(
        context,
        DateTime(Date(exportData.summary!!.timestamp)),
        privateAppFile,
        messages
    )

    moveToDownloads(context, privateAppFile, destination)
}
