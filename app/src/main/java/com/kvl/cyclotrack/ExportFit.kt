package com.kvl.cyclotrack

import android.content.Context
import android.net.Uri
import com.garmin.fit.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import java.util.TimeZone
import kotlin.math.pow
import kotlin.math.roundToInt

val cyclotrackFitAppId = "comkvlcyclotrack".toByteArray()

fun makeFitMessages(
    appId: ByteArray,
    exportData: TripDetailsViewModel.ExportData,
): MutableList<Mesg> {
    val toSemicircles = 2.0.pow(31.0) / 180
    val messages: MutableList<Mesg> = ArrayList()

    exportData.timeStates?.let {
        getStartTime(it)?.let {
            messages.add(EventMesg().apply {
                timestamp = DateTime(Date(it))
                event = Event.TIMER
                eventType = EventType.START
            })
        }
    }

    // Create the Developer Id message for the developer data fields.
    messages.add(DeveloperDataIdMesg().apply {
        for (i in appId.indices) {
            setApplicationId(i, appId[i])
        }
        applicationVersion = BuildConfig.VERSION_CODE.toLong()
        developerDataIndex = 0.toShort()
    })

    exportData.measurements?.forEach {
        messages.add(RecordMesg().apply {
            timestamp = DateTime(Date(it.time))
            speed = it.speed
            heartRate = it.heartRate
            cadence = it.cadenceRpm?.toInt()?.toShort()
            altitude = it.altitude.toFloat()
            positionLat = (it.latitude * toSemicircles).toInt()
            positionLong = (it.longitude * toSemicircles).toInt()
            gpsAccuracy = it.accuracy.roundToInt().toShort()
        })
    }


    // Timer Events are a BEST PRACTICE for FIT ACTIVITY files
    exportData.timeStates?.let {
        getEndTime(it)?.let {
            messages.add(EventMesg().apply {
                timestamp = DateTime(Date(it))
                event = Event.TIMER
                eventType = EventType.STOP_ALL
            })
        }
    }

    // Every FIT ACTIVITY file MUST contain at least one Lap message
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

    // Every FIT ACTIVITY file MUST contain at least one Session message
    messages.add(
        SessionMesg().apply {
            messageIndex = 0
            timestamp =
                DateTime(Date(exportData.summary!!.timestamp + (exportData.summary!!.duration!! * 1000).toLong()))
            startTime = DateTime(getStartTime(exportData.timeStates!!)!!)
            totalElapsedTime = (exportData.summary!!.duration!!).toFloat()
            totalTimerTime = accumulateActiveTime(exportData.timeStates!!).toFloat()
            sport = Sport.CYCLING
            subSport = SubSport.ROAD
            firstLapIndex = 0
            numLaps = exportData.splits!!.size
            exportData.weather?.map { it.temperature }?.average()?.let {
                avgTemperature = kelvinToCelsius(it).roundToInt()
                    .toByte()
            }
        })

    // Every FIT ACTIVITY file MUST contain EXACTLY one Activity message
    messages.add(ActivityMesg().apply {
        timestamp = DateTime(Date(exportData.summary!!.timestamp))
        numSessions = 1
        val timeZone: TimeZone = TimeZone.getDefault()
        val timezoneOffset = timeZone.rawOffset + timeZone.dstSavings
        localTimestamp = DateTime(Date(exportData.summary!!.timestamp + timezoneOffset)).timestamp
        totalTimerTime = accumulateActiveTime(exportData.timeStates!!).toFloat()
    })
    return messages
}

fun writeFitFile(
    startTime: DateTime,
    file: java.io.File,
    messages: List<Mesg?>
) {
    val fileType = File.ACTIVITY
    val manufacturerId = Manufacturer.DEVELOPMENT.toShort()
    val productId: Short = 0
    val softwareVersion = BuildConfig.VERSION_CODE.toFloat()
    val random = Random()
    val serialNumber = random.nextInt()

    // Every FIT file MUST contain a File ID message
    val fileIdMesg = FileIdMesg()
    fileIdMesg.type = fileType
    fileIdMesg.manufacturer = manufacturerId.toInt()
    fileIdMesg.product = productId.toInt()
    fileIdMesg.timeCreated = startTime
    fileIdMesg.serialNumber = serialNumber.toLong()

    // A Device Info message is a BEST PRACTICE for FIT ACTIVITY files
    val deviceInfoMesg = DeviceInfoMesg()
    deviceInfoMesg.deviceIndex = DeviceIndex.CREATOR
    deviceInfoMesg.manufacturer = Manufacturer.DEVELOPMENT
    deviceInfoMesg.product = productId.toInt()
    deviceInfoMesg.productName = "Cyclotrack"
    deviceInfoMesg.serialNumber = serialNumber.toLong()
    deviceInfoMesg.softwareVersion = softwareVersion
    deviceInfoMesg.timestamp = startTime

    val encode =
        FileEncoder(
            file,
            Fit.ProtocolVersion.V2_0
        )
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
    val messages: MutableList<Mesg> = makeFitMessages(cyclotrackFitAppId, exportData)

    val privateAppFile = java.io.File(
        context.filesDir,
        getFileName(context.contentResolver, destination)!!
    )

    writeFitFile(DateTime(Date(exportData.summary!!.timestamp)), privateAppFile, messages)

    moveToDownloads(
        context,
        privateAppFile,
        destination,
    )
}