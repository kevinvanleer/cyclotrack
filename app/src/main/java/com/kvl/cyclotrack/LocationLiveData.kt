package com.kvl.cyclotrack

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import android.util.LogPrinter
import androidx.lifecycle.LiveData
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices


class LocationLiveData(context: Context) : LiveData<LocationModel>() {
    private var fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var startTime: Long = Long.MIN_VALUE

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
                    if (startTime == Long.MIN_VALUE) startTime = it.elapsedRealtimeNanos
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
            for (location in locationResult.locations) {
                location.dump(LogPrinter(Log.VERBOSE, "LOCATION"), "kvl")
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
        var accurateEnough = new.hasAccuracy() && new.accuracy < 4f

        /*accurateEnough = when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O -> new.speedAccuracyMetersPerSecond > 0f && new.speed > new.speedAccuracyMetersPerSecond
            else -> new.hasAccuracy() && new.accuracy < 5f
        }*/

        val newDuration =
            if (startTime != Long.MIN_VALUE) new.elapsedRealtimeNanos - startTime else 0

        if(accurateEnough) {
            newDistance = old?.location?.distanceTo(new)?.plus(old.distance) ?: oldDistance
            val oldAltitude: Double = old?.location?.altitude ?: 0.0
            //val newSlope = if(newDistance == 0.0) 0.0 else (new.altitude - oldAltitude) / newDistance
            val newSlope = (new.altitude - oldAltitude) / newDistance
            val newSpeed : Float = if (newDuration == 0L) 0f else ((newDistance - (old?.distance ?: 0.0)).toFloat() / (newDuration - (old?.duration ?: 0L)))

            Log.v("LOCATION_MODEL_NEW",
                "speed: ${newSpeed}; distance: $newDistance; slope: $newSlope; duration: $newDuration")
            value = LocationModel(location = new,
                speed = newSpeed,
                distance = newDistance,
                slope = newSlope,
                duration = newDuration)
        } else {
            Log.v("LOCATION_MODEL_PLACEHOLDER", "${newDuration.toString()}")
            var newValue = value?.copy(duration = newDuration) ?: LocationModel(duration = newDuration, speed = 0f, distance = 0.0, slope = 0.0, location = null)
            value = newValue
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
    val speed: Float,
    val distance: Double,
    val slope: Double,
    val duration: Long,
)