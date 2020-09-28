package com.kvl.cyclotrack

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices


class LocationLiveData(context: Context) : LiveData<LocationModel>() {
    private var fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var startTime: Double = Double.NaN
    private val ACCURACY_THRESHOLD = 10f
    private val SPEED_THRESHOLD = 1e-10
    private val MAXIMUM_SPEED = 20f
    private val MAXIMUM_ACCELERATION = 2f

    override fun onInactive() {
        super.onInactive()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    override fun onActive() {
        super.onActive()
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.also {
                    if (!startTime.isFinite()) startTime = it.elapsedRealtimeNanos / 1e9
                    setLocationModel(value, it)
                }
            }
        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult ?: return
            Log.v("LOCATION", "New location result")
            for (location in locationResult.locations) {
                //location.dump(LogPrinter(Log.VERBOSE, "LOCATION"), "kvl")
                Log.v("LOCATION",
                    "location: ${location.latitude},${location.longitude} +/- ${location.accuracy}m")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    Log.v("LOCATION",
                        "bearing: ${location.bearing} +/- ${location.bearingAccuracyDegrees}deg")
                    Log.v("LOCATION",
                        "speed: ${location.speed} +/- ${location.speedAccuracyMetersPerSecond}m/s")
                    Log.v("LOCATION",
                        "altitude: ${location.altitude} +/- ${location.verticalAccuracyMeters}m")
                }

                setLocationModel(value, location)
            }
        }
    }

    private fun setLocationModel(old: LocationModel?, new: Location) {
        val oldDistance: Double = old?.distance ?: 0.0
        var newDistance: Double = oldDistance
        var accurateEnough = new.hasAccuracy() && new.accuracy < ACCURACY_THRESHOLD


        val newDuration =
            if (startTime.isFinite()) (new.elapsedRealtimeNanos / 1e9) - startTime else 0.0
        val durationDelta = (newDuration - (old?.duration ?: 0.0))

        if (accurateEnough) {
            val distanceDelta = old?.location?.distanceTo(new)?.toDouble() ?: 0.0
            val newSpeed: Float =
                if (newDuration == 0.0) 0f else (distanceDelta / durationDelta).toFloat()
            if (newSpeed > SPEED_THRESHOLD) newDistance += distanceDelta
            val oldAltitude: Double = old?.location?.altitude ?: 0.0
            val newSlope =
                if (newSpeed > SPEED_THRESHOLD) (new.altitude - oldAltitude) / distanceDelta else old?.slope
                    ?: 0.0
            val newAcceleration = (newSpeed - (old?.speed ?: 0f) / durationDelta).toFloat()

            Log.d("SPEED",
                if (newDuration == 0.0) "0" else (distanceDelta / durationDelta).toFloat().toString())
            Log.d("SPEED_DISTANCE_DELTA", distanceDelta.toString())
            Log.d("SPEED_DURATION_DELTA", durationDelta.toString())

            Log.v("LOCATION_MODEL_NEW",
                "accuracy: ${new.accuracy}; speed: ${newSpeed}; acceleration: ${newAcceleration}; distance: $newDistance; slope: $newSlope; duration: $newDuration")

            value = LocationModel(location = new,
                speed = newSpeed,
                distance = newDistance,
                acceleration = newAcceleration,
                slope = newSlope,
                duration = newDuration,
                accuracy = new.accuracy,
                tracking = true)
        } else {
            Log.v("LOCATION_MODEL_PLACEHOLDER", "$newDuration")
            value =
                value?.copy(duration = newDuration, accuracy = new.accuracy, tracking = false)
                    ?: LocationModel(duration = newDuration,
                        speed = 0f,
                        acceleration = 0f,
                        distance = 0.0,
                        slope = 0.0,
                        location = null,
                        accuracy = new.accuracy,
                        tracking = false)
        }
    }

    companion object {
        val locationRequest = LocationRequest.create()?.apply {
            interval = 1000
            fastestInterval = 100
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }
}

data class LocationModel(
    val location: Location?,
    val accuracy: Float,
    val speed: Float,
    val acceleration: Float,
    val distance: Double,
    val slope: Double,
    val duration: Double,
    val tracking: Boolean,
)