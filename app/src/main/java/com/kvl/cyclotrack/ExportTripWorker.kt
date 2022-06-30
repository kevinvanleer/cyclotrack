package com.kvl.cyclotrack

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

@HiltWorker
class ExportTripWorker @AssistedInject constructor(
    @Assisted val appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    val logTag = "ExportTripWorker"
    private val xlsxMime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private val binaryMime = "application/octet-stream"

    @Inject
    lateinit var tripsRepository: TripsRepository

    @Inject
    lateinit var measurementsRepository: MeasurementsRepository

    @Inject
    lateinit var timeStateRepository: TimeStateRepository

    @Inject
    lateinit var splitRepository: SplitRepository

    @Inject
    lateinit var onboardSensorsRepository: OnboardSensorsRepository

    @Inject
    lateinit var weatherRepository: WeatherRepository

    private suspend fun exportTripData(
        contentResolver: ContentResolver,
        tripId: Long,
        uri: Uri,
        fileType: String,
    ) {
        Log.d(logTag, "exportTripData")
        val mime = when (fileType) {
            "xlsx" -> xlsxMime
            else -> binaryMime
        }

        fun getUriFilePart(): String? {
            val result = uri.path
            val cut = result!!.lastIndexOf('/')
            return if (cut != -1) {
                result.substring(cut + 1)
            } else null
        }

        fun getFileName(): String? {
            return if (uri.scheme == "content") {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else null
                }
            } else {
                getUriFilePart()
            }
        }

        val inProgressBuilder = NotificationCompat.Builder(
            appContext,
            appContext.getString(R.string.notification_export_trip_in_progress_id)
        )
            .setSmallIcon(R.drawable.ic_cyclotrack_notification)
            .setContentTitle("Export in progress...")
            .setProgress(1, 0, true)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Data export \"${getFileName()}\" will finish soon.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        val inProgressId = getUriFilePart()?.toIntOrNull() ?: 0
        with(NotificationManagerCompat.from(appContext)) {
            Log.d(logTag, "notify in progress")
            notify(inProgressId, inProgressBuilder.build())
        }
        appContext.mainExecutor.execute {
            Toast.makeText(
                appContext,
                "You'll be notified when the export is complete.",
                Toast.LENGTH_SHORT
            ).show()
        }
        Log.d(logTag, "building export data")
        val exportData = TripDetailsViewModel.ExportData(
            summary = tripsRepository.get(tripId),
            measurements = measurementsRepository.get(tripId),
            timeStates = timeStateRepository.getTimeStates(tripId),
            splits = splitRepository.getTripSplits(tripId),
            onboardSensors = onboardSensorsRepository.get(tripId),
            weather = weatherRepository.getTripWeather(tripId)
        )
        if (exportData.summary != null &&
            exportData.measurements != null &&
            exportData.timeStates != null &&
            exportData.splits != null &&
            exportData.onboardSensors != null &&
            exportData.weather != null
        ) {
            Log.d(
                logTag,
                "Exporting trip $tripId..."
            )

            try {
                when (fileType) {
                    "xlsx" -> exportRideToXlsx(
                        contentResolver,
                        uri,
                        exportData
                    )
                    else -> exportRideToFit(appContext, uri, exportData)
                }

                val viewFileIntent = Intent().apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(uri, mime)
                    flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                val viewFilePendingIntent = PendingIntent.getActivity(
                    appContext,
                    uri.hashCode() * 100 + 1,
                    viewFileIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )

                val chooserIntent = Intent.createChooser(Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    setDataAndType(uri, mime)
                    flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
                }, "title")
                val sharePendingIntent = PendingIntent.getActivity(
                    appContext,
                    uri.hashCode() * 100 + 2,
                    chooserIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )

                val deleteIntent = Intent(
                    appContext,
                    DeleteExportBroadcastReceiver::class.java
                ).apply {
                    action = appContext.getString(R.string.intent_action_delete_exported_data)
                    putExtra("TRIP_ID", exportData.summary?.id)
                    data = uri
                }
                val deletePendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    uri.hashCode() * 100 + 3,
                    deleteIntent,
                    PendingIntent.FLAG_ONE_SHOT
                )
                val builder = NotificationCompat.Builder(
                    appContext,
                    appContext.getString(R.string.notification_export_trip_id)
                )
                    .setSmallIcon(R.drawable.ic_cyclotrack_notification)
                    .setContentTitle("Export complete!")
                    .setContentIntent(viewFilePendingIntent)
                    .setAutoCancel(true)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText("Data export \"${getFileName()}\" is ready in your downloads folder.")
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .addAction(
                        0,
                        "SHARE",
                        sharePendingIntent
                    )
                    .addAction(
                        0,
                        "DELETE",
                        deletePendingIntent
                    )
                with(NotificationManagerCompat.from(appContext)) {
                    Log.d(logTag, "notify export complete")
                    cancel(inProgressId)
                    notify(exportData.summary?.id?.toInt() ?: 0, builder.build())
                }
            } catch (e: RuntimeException) {
                Log.e(logTag, "Export failed", e)
                FirebaseCrashlytics.getInstance().recordException(e)

                val builder = NotificationCompat.Builder(
                    appContext,
                    appContext.getString(R.string.notification_export_trip_id)
                )
                    .setSmallIcon(R.drawable.ic_cyclotrack_notification)
                    .setContentTitle("Export failed!")
                    .setAutoCancel(true)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText("There was an error processing data export \"${getFileName()}\".")
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                with(NotificationManagerCompat.from(appContext)) {
                    Log.d(logTag, "notify export complete")
                    cancel(inProgressId)
                    notify(exportData.summary?.id?.toInt() ?: 0, builder.build())
                }
                throw e
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d(logTag, "starting ExportTripWorker")
        val fileType = inputData.getString("fileType") ?: "csv"
        inputData.getLong("tripId", -1).takeIf { it >= 0 }?.let { tripId ->
            val prefix = when (FeatureFlags.devBuild) {
                true -> "cyclotrack-dev"
                else -> "cyclotrack"
            }
            val trip = tripsRepository.get(tripId)
            val fileName = "${prefix}_${
                String.format("%06d", trip.id)
            }_${trip.name?.trim()?.replace(" ", "-") ?: "unknown"}.$fileType"

            val downloadsFolder =
                when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    true -> MediaStore.Downloads.getContentUri(
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                    else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }
            Log.d(logTag, "Export filename: $fileName")
            Log.d(logTag, "Export to: ${downloadsFolder.path}")
            val exportDetails = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            }

            appContext.contentResolver.query(
                downloadsFolder,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(fileName),
                "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
            ).use { cursor ->
                if (cursor!!.count > 0) {
                    Log.d(
                        logTag, "Removing previous export of ${fileName}"
                    )
                    appContext.contentResolver.delete(
                        downloadsFolder,
                        "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                        arrayOf(fileName)
                    )
                }
            }

            appContext.contentResolver.insert(downloadsFolder, exportDetails)?.let { uri ->
                try {
                    exportTripData(appContext.contentResolver, tripId, uri, fileType)
                } catch (e: RuntimeException) {
                    return Result.failure()
                }
            } ?: return Result.failure()
        }
        return Result.success()
    }
}
