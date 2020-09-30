package com.kvl.cyclotrack

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.LiveData
import com.google.android.gms.location.LocationServices
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max


class LocationLiveData(context: Context) : LiveData<LocationModel>() {
    //private var fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
    private var startTime: Double = Double.NaN
    private var splitTime: Double = 0.0
    private val accuracyThreshold = 7.5f
    private val defaultSpeedThreshold = 0.5f

    private val locationListener = object: LocationListener {
        override fun onLocationChanged(location: Location?) {
            Log.v("LOCATION", "New location result")
            if(location != null) {
                Log.v("LOCATION",
                    "location: ${location.latitude},${location.longitude} +/- ${location.accuracy}m")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    Log.v("LOCATION",
                        "bearing: ${location.bearing} +/- ${location.bearingAccuracyDegrees}deg")
                    Log.v("LOCATION",
                        "speed: ${location.speed} +/- ${location.speedAccuracyMetersPerSecond}m/s")
                    Log.v("LOCATION",
                        "altitude: ${location.altitude} +/- ${location.verticalAccuracyMeters}m")
                    Log.v("LOCATION", "timestamp: ${location.elapsedRealtimeNanos}; ${location.time}")
                }
                setLocationModel(value, location)
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            TODO("Not yet implemented")
        }

        override fun onProviderEnabled(provider: String?) {
            TODO("Not yet implemented")
        }

        override fun onProviderDisabled(provider: String?) {
            TODO("Not yet implemented")
        }
    }

    override fun onInactive() {
        super.onInactive()
        //fusedLocationClient.removeLocationUpdates(locationCallback)
        locationManager.removeUpdates(locationListener)
    }

    @SuppressLint("MissingPermission")
    override fun onActive() {
        super.onActive()
        /*fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.also {
                    if (!startTime.isFinite()) startTime = it.elapsedRealtimeNanos / 1e9
                    setLocationModel(value, it)
                }
            }
        startLocationUpdates()*/

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100L, 1f, locationListener)
    }

    /*
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
    */

    private fun setLocationModel(old: LocationModel?, new: Location) {
        val oldDistance: Double = old?.distance ?: 0.0
        var newDistance: Double = oldDistance
        var accurateEnough = new.hasAccuracy() && new.accuracy < accuracyThreshold
        var speedThreshold = defaultSpeedThreshold

        if (!startTime.isFinite()) startTime = new.elapsedRealtimeNanos / 1e9

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (new.hasSpeedAccuracy()) {
                //accurateEnough = accurateEnough && new.speed > new.speedAccuracyMetersPerSecond
                speedThreshold = max(defaultSpeedThreshold, new.speedAccuracyMetersPerSecond * 1.5f)
            }
        }

        val newDuration =
            if (startTime.isFinite()) (new.elapsedRealtimeNanos / 1e9) - startTime else 0.0
        //val durationDelta = (newDuration - (old?.duration ?: 0.0))
        val durationDelta =
            newDuration - if (startTime.isFinite()) ((old?.location?.elapsedRealtimeNanos
                ?: 0L) / 1e9) - startTime else 0.0

        if (accurateEnough) {
            val distanceDelta = old?.location?.distanceTo(new)?.toDouble() ?: 0.0

            //val newSpeed: Float =
            //    if (newDuration == 0.0) 0f else (distanceDelta / durationDelta).toFloat()
            val newSpeed = if (new.speed > speedThreshold) new.speed else 0f

            var newSplitSpeed = old?.splitSpeed ?: 0f

            if (new.speed > speedThreshold) newDistance += distanceDelta

            if (floor(newDistance * 0.000621371) > floor((old?.distance ?: Double.MAX_VALUE) * 0.000621371)) {
                newSplitSpeed = (1609.34f / (newDuration - splitTime)).toFloat()
                splitTime = newDuration
            }

            val oldAltitude: Double = old?.location?.altitude ?: 0.0
            val verticalSpeed = abs((new.altitude - oldAltitude) / durationDelta)
            Log.v("VERTICAL_SPEED", verticalSpeed.toString())
            var newSlope = 0.0
            if (verticalSpeed < newSpeed && distanceDelta != 0.0) {
                val slopeAlpha = 0.5
                newSlope = slopeAlpha * (
                        if (new.speed > speedThreshold) ((new.altitude - oldAltitude) / distanceDelta)
                        else (old?.slope ?: 0.0)
                        ) + ((1 - slopeAlpha) * (old?.slope ?: 0.0))
                Log.v("SLOPE", newSlope.toString())
            }
            val newAcceleration =
                if (durationDelta == 0.0) 0f
                else ((newSpeed - (old?.speed ?: 0f)) / durationDelta).toFloat()

            Log.d("SPEED", newSpeed.toString())
            Log.d("SPEED_DISTANCE_DELTA", distanceDelta.toString())
            Log.d("SPEED_DURATION_DELTA", durationDelta.toString())

            Log.v("LOCATION_MODEL_NEW",
                "accuracy: ${new.accuracy}; speed: ${newSpeed}; acceleration: ${newAcceleration}; distance: $newDistance; slope: $newSlope; duration: $newDuration")

            Log.d("MAX_ACCELERATION", max(newAcceleration, old?.maxAcceleration ?: 0f).toString())

            value =
                LocationModel(location = new,
                    speed = newSpeed,
                    maxSpeed = max(if (newSpeed.isFinite()) newSpeed else 0f,
                        old?.maxSpeed ?: 0f),
                    distance = newDistance,
                    acceleration = newAcceleration,
                    maxAcceleration = max(if (newAcceleration.isFinite()) newAcceleration else 0f,
                        old?.maxAcceleration ?: 0f),
                    slope = newSlope,
                    duration = newDuration,
                    accuracy = new.accuracy,
                    splitSpeed = newSplitSpeed,
                    tracking = true)

        } else {
            value =
                value?.copy(duration = newDuration, accuracy = new.accuracy, tracking = false)
                    ?: LocationModel(duration = newDuration,
                        speed = 0f,
                        maxSpeed = 0f,
                        acceleration = 0f,
                        maxAcceleration = 0f,
                        distance = 0.0,
                        slope = 0.0,
                        splitSpeed = 0f,
                        location = null,
                        accuracy = new.accuracy,
                        tracking = false)
        }
    }
    /*
    companion object {
        val locationRequest = LocationRequest.create()?.apply {
            interval = 1000
            fastestInterval = 100
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }
    */
}

data class LocationModel(
    val location: Location?,
    val accuracy: Float,
    val speed: Float,
    val splitSpeed: Float,
    val maxSpeed: Float,
    val acceleration: Float,
    val maxAcceleration: Float,
    val distance: Double,
    val slope: Double,
    val duration: Double,
    val tracking: Boolean,
)