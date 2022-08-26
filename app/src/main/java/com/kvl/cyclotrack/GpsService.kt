package com.kvl.cyclotrack

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpsService @Inject constructor(context: Application) : LiveData<Location>() {
    private val logTag = "GpsService"
    private val context = context
    var accessGranted = MutableLiveData(false)
    private val locationManager =
        (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.v(logTag, "New location result")
            Log.v(
                logTag,
                "location: ${location.latitude},${location.longitude} +/- ${location.accuracy}m"
            )
            Log.v(
                logTag,
                "bearing: ${location.bearing} +/- ${location.bearingAccuracyDegrees}deg"
            )
            Log.v(
                logTag,
                "speed: ${location.speed} +/- ${location.speedAccuracyMetersPerSecond}m/s"
            )
            Log.v(
                logTag,
                "altitude: ${location.altitude} +/- ${location.verticalAccuracyMeters}m"
            )
            Log.v(
                logTag,
                "timestamp: ${location.elapsedRealtimeNanos}; ${location.time}"
            )
            value = location
        }

        /*override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Log.d(logTag, "GPS status changed")
        }*/

        override fun onProviderEnabled(provider: String) {
            Log.d(logTag, "GPS provider enabled")
            accessGranted.value = true
        }

        override fun onProviderDisabled(provider: String) {
            Log.d(logTag, "GPS provider disabled")
            accessGranted.value = false
        }
    }

    init {
        accessGranted.value = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        startListening()
        Log.d(logTag, "GPS service initialized")
    }

    fun stopListening() {
        locationManager.removeUpdates(locationListener)
    }

    fun startListening() {
        accessGranted.value = true
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(logTag, "User has not granted permission to access fine location data")
            accessGranted.value = false
            return
        }

        //This can safely be called multiple times, will only be registered once
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000L,
            1f,
            locationListener
        )
    }
}
