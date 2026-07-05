package com.kvl.cyclotrack.util

import com.google.firebase.crashlytics.FirebaseCrashlytics

// Consent-exempt operational telemetry: reported as Crashlytics non-fatals (always
// collected, unlike FirebaseAnalytics which is gated on the analytics opt-in).
// Each subclass groups as its own Crashlytics issue; the message carries the data.
sealed class TelemetryEvent(message: String) : Exception(message) {
    class OnboardSensorsMigrationStarted(rides: Int) : TelemetryEvent("rides=$rides")
    class OnboardSensorsMigrationFinished(rides: Int) : TelemetryEvent("rides=$rides")
    class UpdateInstalled(from: Long, to: Long) : TelemetryEvent("from=$from to=$to")
}

fun emitTelemetry(event: TelemetryEvent) =
    FirebaseCrashlytics.getInstance().recordException(event)
