package com.kvl.cyclotrack

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties

fun getMeasurementsHeaderRow(): String {
    var row: String = ""
    for (prop in Measurements::class.declaredMemberProperties) {
        row += "${prop.name},"
    }
    return row
}

fun getRideMeasurementsCsv(measurements: Array<Measurements>): Array<String> {
    val rows = ArrayList<String>()

    rows.add(getMeasurementsHeaderRow())

    measurements.forEach { measurement ->
        var row: String = ""
        for (prop in Measurements::class.declaredMemberProperties) {
            row += "${prop.call(measurement).toString()},"
        }
        rows.add(row)
    }
    return rows.toTypedArray()
}

inline fun <reified T : Any> getDataCsv(measurements: Array<T>): Array<String> {
    val rows = ArrayList<String>()

    rows.add(getDataHeaderRow<T>())
    val reflection: KClass<T> = T::class

    measurements.forEach { measurement ->
        var row: String = ""
        for (prop in reflection.declaredMemberProperties) {
            row += "${prop.call(measurement).toString()},"
        }
        rows.add(row)
    }
    return rows.toTypedArray()
}

inline fun <reified T : Any> getDataHeaderRow(): String {
    var row: String = ""
    val reflection: KClass<T> = T::class
    for (prop in reflection.declaredMemberProperties) {
        row += "${prop.name},"
    }
    return row
}

fun exportRideToCsv(
    contentResolver: ContentResolver,
    filePath: Uri,
    trip: Trip,
    measurements: Array<Measurements>,
    timeStates: Array<TimeState>,
) {
    Log.d("EXPORT_SERVICE", "Exporting...")
    contentResolver.openFileDescriptor(filePath, "w")?.use {
        Log.d("EXPORT_SERVICE", "Opening...")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(it.fileDescriptor))).use { stream ->
            Log.d("EXPORT_SERVICE", "Zipping...")
            stream.putNextEntry(ZipEntry("${String.format("%06d", trip.id)}_measurements.csv"))
            val measurementsCsv = getDataCsv(measurements)
            measurementsCsv.forEach { row ->
                stream.write("$row\n".toByteArray())
            }
            stream.closeEntry()

            stream.putNextEntry(ZipEntry("${String.format("%06d", trip.id)}_timeStates.csv"))

            val timeStatesCsv = getDataCsv(timeStates)
            timeStatesCsv.forEach { row ->
                stream.write("$row\n".toByteArray())
            }
            stream.closeEntry()
            stream.finish()
            stream.close()
        }
    }
}

inline fun <reified T : Any> getDataHeaderRowXlsx(headerRow: Row) {
    val reflection: KClass<T> = T::class
    for (prop in reflection.declaredMemberProperties) {
        headerRow.createCell(headerRow.count()).setCellValue(prop.name)
    }
}

inline fun <reified T : Any> addDataToSheet(sheet: Sheet, data: Array<T>) {
    getDataHeaderRowXlsx<T>(sheet.createRow(0))
    val reflection: KClass<T> = T::class

    data.forEach { measurement ->
        //populateRow(sheet, reflection, measurement)
        val row = sheet.createRow(sheet.count())
        for (prop in reflection.declaredMemberProperties) {
            prop.get(measurement).toString().let { stringVal ->
                stringVal.toDoubleOrNull()?.let { doubleVal ->
                    row.createCell(row.count())
                        .setCellValue(doubleVal)
                } ?: row.createCell(row.count())
                    .setCellValue(stringVal)
            }
        }
    }
}

inline fun <reified T : Any> addDataToSheet(sheet: Sheet, data: T) {
    val reflection: KClass<T> = T::class

    getDataHeaderRowXlsx<T>(sheet.createRow(0))
    //populateRow(sheet, reflection, data)
    val row = sheet.createRow(sheet.count())
    for (prop in reflection.declaredMemberProperties) {
        prop.get(data).toString().let { stringVal ->
            stringVal.toDoubleOrNull()?.let { doubleVal ->
                row.createCell(row.count())
                    .setCellValue(doubleVal)
            } ?: row.createCell(row.count())
                .setCellValue(stringVal)
        }
    }
}

inline fun <reified T : Any> populateRow(
    sheet: Sheet,
    reflection: KClass<T>,
    data: T,
) {
    val row = sheet.createRow(sheet.count())
    for (prop in reflection.declaredMemberProperties) {
        prop.get(data).toString().let { stringVal ->
            stringVal.toDoubleOrNull()?.let { doubleVal ->
                row.createCell(row.count())
                    .setCellValue(doubleVal)
            } ?: row.createCell(row.count())
                .setCellValue(stringVal)
        }
    }
}

fun exportRideToXlsx(
    contentResolver: ContentResolver,
    filePath: Uri,
    exportData: TripDetailsViewModel.ExportData,
) {
    val workbook: Workbook = XSSFWorkbook()

    addDataToSheet(workbook.createSheet("summary"), exportData.summary!!)
    addDataToSheet(workbook.createSheet("measurements"), exportData.measurements!!)
    addDataToSheet(workbook.createSheet("timeStates"), exportData.timeStates!!)
    addDataToSheet(workbook.createSheet("splits"), exportData.splits!!)

    contentResolver.openFileDescriptor(filePath, "w")?.use {
        FileOutputStream(it.fileDescriptor).use { stream ->
            workbook.write(stream)
        }
    }
}

/*
fun exportRideToXlsx(
    contentResolver: ContentResolver,
    filePath: Uri,
    exportData: TripDetailsViewModel.ExportData,
) {
    val workbook: Workbook = XSSFWorkbook()

    for (prop in TripDetailsViewModel.ExportData::class.declaredMemberProperties) {
        addDataToSheet(workbook.createSheet(prop.name), prop.get(exportData) as prop.returnType)
    }

    contentResolver.openFileDescriptor(filePath, "w")?.use {
        FileOutputStream(it.fileDescriptor).use { stream ->
            workbook.write(stream)
        }
    }
}
*/
