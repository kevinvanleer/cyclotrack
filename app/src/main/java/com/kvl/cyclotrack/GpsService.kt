package com.kvl.cyclotrack

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.LiveData
import javax.inject.Inject

class GpsService @Inject constructor(context: Application): LiveData<Location>() {
    private val locationManager = (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
    private val locationListener = object: LocationListener {
        override fun onLocationChanged(location: Location?) {
            Log.v("GPS_SERVICE", "New location result")
            if (location != null) {
                Log.v("GPS_SERVICE",
                    "location: ${location.latitude},${location.longitude} +/- ${location.accuracy}m")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    Log.v("GPS_SERVICE",
                        "bearing: ${location.bearing} +/- ${location.bearingAccuracyDegrees}deg")
                    Log.v("GPS_SERVICE",
                        "speed: ${location.speed} +/- ${location.speedAccuracyMetersPerSecond}m/s")
                    Log.v("GPS_SERVICE",
                        "altitude: ${location.altitude} +/- ${location.verticalAccuracyMeters}m")
                    Log.v("GPS_SERVICE",
                        "timestamp: ${location.elapsedRealtimeNanos}; ${location.time}")
                }
            }
            value = location
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

    init {
        startListening()
    }

    fun stopListening() {
        locationManager.removeUpdates(locationListener)
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100L, 1f, locationListener)
    }
}