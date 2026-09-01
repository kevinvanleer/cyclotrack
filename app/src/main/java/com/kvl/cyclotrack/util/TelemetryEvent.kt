package com.kvl.cyclotrack.util

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics

// Operational telemetry, reported on two channels: Crashlytics non-fatals
// (consent-exempt, always collected — the fleet gate for destructive migrations)
// and a parallel FirebaseAnalytics event (gated on the analytics opt-in, but
// queryable/graphable in the GA console). Each subclass groups as its own
// Crashlytics issue and GA event name; the params carry the data.
sealed class TelemetryEvent(
    val eventName: String,
    message: String,
    val params: Map<String, Long> = emptyMap(),
) : Exception(message) {
    class OnboardSensorsMigrationStarted(rides: Int) : TelemetryEvent(
        "OnboardSensorsMigrationStarted", "rides=$rides", mapOf("rides" to rides.toLong())
    )

    class OnboardSensorsMigrationFinished(rides: Int) : TelemetryEvent(
        "OnboardSensorsMigrationFinished", "rides=$rides", mapOf("rides" to rides.toLong())
    )

    class UpdateInstalled(from: Long, to: Long) : TelemetryEvent(
        "UpdateInstalled", "from=$from to=$to", mapOf("from" to from, "to" to to)
    )
}

fun emitTelemetry(context: Context, event: TelemetryEvent) {
    FirebaseCrashlytics.getInstance().recordException(event)
    FirebaseAnalytics.getInstance(context.applicationContext)
        .logEvent(event.eventName) {
            event.params.forEach { (key, value) -> param(key, value) }
        }
}
