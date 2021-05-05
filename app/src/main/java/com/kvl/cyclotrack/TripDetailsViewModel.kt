package com.kvl.cyclotrack

import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class TripDetailsViewModel @ViewModelInject constructor(
    private val tripsRepository: TripsRepository,
    private val measurementsRepository: MeasurementsRepository,
    private val timeStateRepository: TimeStateRepository,
    private val splitRepository: SplitRepository,
    private val onboardSensorsRepository: OnboardSensorsRepository,
    private val googleFitApiService: GoogleFitApiService,
    private val sharedPreferences: SharedPreferences,
) : ViewModel() {
    val logTag = "TRIP_DETAILS_VIEW_MODEL"
    var tripId: Long = 0

    fun tripOverview() = tripsRepository.getTrip(tripId)
    fun timeState() = timeStateRepository.getTimeStates(tripId)
    fun splits() = splitRepository.getTripSplits(tripId)
    fun clearSplits() =
        viewModelScope.launch {
            splitRepository.removeTripSplits(tripId)
        }

    fun removeTrip() =
        viewModelScope.launch { tripsRepository.removeTrip(tripId) }

    fun measurements() = measurementsRepository.getTripCriticalMeasurements(tripId)
    fun exportMeasurements() = measurementsRepository.getTripMeasurements(tripId)
    fun onboardSensors() = onboardSensorsRepository.getTripMeasurements(tripId)

    suspend fun getCombinedBiometrics(timestamp: Long): Biometrics {
        var biometrics = getBiometrics(0, sharedPreferences)
        Log.d(logTag, "biometrics prefs: ${biometrics}")

        viewModelScope.launch {
            val tripBiometrics = tripsRepository.getDefaultBiometrics(tripId)
            Log.d(logTag, "trip biometrics for ${tripId}: ${tripBiometrics}")
            biometrics = biometrics.copy(
                userSex = tripBiometrics?.userSex ?: biometrics?.userSex,
                userHeight = tripBiometrics?.userHeight ?: biometrics?.userHeight,
                userWeight = tripBiometrics?.userWeight ?: biometrics?.userWeight,
                userAge = tripBiometrics?.userAge ?: biometrics?.userAge,
                userVo2max = tripBiometrics?.userVo2max ?: biometrics?.userVo2max,
                userRestingHeartRate = tripBiometrics?.userRestingHeartRate
                    ?: biometrics?.userRestingHeartRate,
                userMaxHeartRate = tripBiometrics?.userMaxHeartRate
                    ?: biometrics?.userMaxHeartRate,
            )
            Log.d(logTag, "biometrics after trip: ${biometrics}")
            if (googleFitApiService.hasPermission()) {
                val weightDeferred = async { googleFitApiService.getLatestWeight(timestamp) }
                val heightDeferred = async { googleFitApiService.getLatestHeight(timestamp) }
                val hrDeferred = async { googleFitApiService.getLatestRestingHeartRate(timestamp) }

                weightDeferred.await().let {
                    Log.d(logTag, "google weight: ${it}")
                    biometrics = biometrics.copy(userWeight = it)
                }
                heightDeferred.await().let {
                    Log.d(logTag, "google height: ${it}")
                    biometrics = biometrics.copy(userHeight = it)
                }
                hrDeferred.await().let {
                    Log.d(logTag, "google resting hr: ${it}")
                    biometrics = biometrics.copy(userRestingHeartRate = it)
                }
                Log.d(logTag, "biometrics google: ${biometrics}")
            }
        }.join()

        Log.d(logTag, "biometrics: ${biometrics}")
        return biometrics
    }

    data class ExportData(
        var summary: Trip? = null,
        var measurements: Array<Measurements>? = null,
        var timeStates: Array<TimeState>? = null,
        var splits: Array<Split>? = null,
        var onboardSensors: Array<OnboardSensors>? = null,
    )

    fun exportData() = MediatorLiveData<ExportData>().apply {
        value = ExportData()
        addSource(tripOverview()) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export trip")
            value = value?.copy(summary = it)
        }
        addSource(exportMeasurements()) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export measurements")
            value = value?.copy(measurements = it)
        }
        addSource(timeState()) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export timeStates")
            value = value?.copy(timeStates = it)
        }
        addSource(splits()) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export splits")
            value = value?.copy(splits = it)
        }
        addSource(onboardSensors()) {
            Log.d("TRIP_DETAILS_VIEW_MODEL", "Updating export onboardSensors")
            value = value?.copy(onboardSensors = it)
        }
    }

    fun addSplits() {
        val combined = zipLiveData(measurements(), timeState())
        combined.observeForever(object :
            Observer<Pair<Array<CriticalMeasurements>, Array<TimeState>>> {
            override fun onChanged(pair: Pair<Array<CriticalMeasurements>, Array<TimeState>>) {
                val measurements = pair.first
                val timeStates = pair.second

                if (measurements.isNotEmpty()) {
                    val tripSplits =
                        calculateSplits(tripId, measurements, timeStates, sharedPreferences)
                    viewModelScope.launch {
                        Log.d("TRIP_DETAILS_VIEW_MODEL",
                            "Inserting post-trip computed splits in database")
                        splitRepository.addSplits(tripSplits.toTypedArray())
                    }
                    combined.removeObserver(this)
                }
            }
        })
    }

}