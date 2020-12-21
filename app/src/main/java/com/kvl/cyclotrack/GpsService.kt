package com.kvl.cyclotrack

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import javax.inject.Inject

class GpsService @Inject constructor(context: Application) : LiveData<Location>() {
    private val context = context;
    var accessGranted = MutableLiveData(false);
    private val locationManager =
        (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
    private val locationListener = object : LocationListener {
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
            Log.d("GPS_SERVICE", "GPS status changed")
        }

        override fun onProviderEnabled(provider: String?) {
            Log.d("GPS_SERVICE", "GPS provider enabled")
            accessGranted.value = true;
        }

        override fun onProviderDisabled(provider: String?) {
            Log.d("GPS_SERVICE", "GPS provider disabled")
            accessGranted.value = false;
        }
    }

    init {
        accessGranted.value = ActivityCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        startListening()
    }

    fun stopListening() {
        locationManager.removeUpdates(locationListener)
    }

    fun startListening() {
        accessGranted.value = true;
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.d("GPS_SERVICE", "User has not granted permission to access fine location data")
            accessGranted.value = false;
            return
        }
        //accessGranted = true;
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
            1000L,
            1f,
            locationListener)
    }
}