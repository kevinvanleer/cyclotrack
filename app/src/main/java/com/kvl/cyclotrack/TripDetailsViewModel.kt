package com.kvl.cyclotrack

import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
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

    fun googleFitWeight(timestamp: Long) =
        googleFitApiService.getLatestWeight(timestamp)

    fun googleFitHeight(timestamp: Long) =
        googleFitApiService.getLatestHeight(timestamp)

    fun googleFitRestingHeartRate(timestamp: Long) =
        googleFitApiService.getLatestRestingHeartRate(timestamp)

    fun removeTrip() =
        viewModelScope.launch { tripsRepository.removeTrip(tripId) }

    suspend fun getTripBiometrics() = tripsRepository.getDefaultBiometrics(tripId)

    fun measurements() = measurementsRepository.getTripCriticalMeasurements(tripId)
    fun exportMeasurements() = measurementsRepository.getTripMeasurements(tripId)
    fun onboardSensors() = onboardSensorsRepository.getTripMeasurements(tripId)

    fun getCombinedBiometrics(timestamp: Long) = MediatorLiveData<Biometrics>().apply {
        var tripBiometrics = MutableLiveData<Biometrics>(null)
        viewModelScope.launch { tripBiometrics.postValue(getTripBiometrics()) }
        value = getBiometrics(0, sharedPreferences)

        if (googleFitApiService.hasPermission()) {
            addSource(googleFitWeight(timestamp)) {
                Log.d(logTag, "Using Google Fit weight: ${it}")
                value = value?.copy(userWeight = it)
            }

            addSource(googleFitHeight(timestamp)) {
                Log.d(logTag, "Using Google Fit height: ${it}")
                value = value?.copy(userHeight = it)
            }

            addSource(googleFitRestingHeartRate(timestamp)) {
                Log.d(logTag, "Using Google Fit resting HR: ${it}")
                value = value?.copy(userRestingHeartRate = it)
            }
        }

        addSource(tripBiometrics) {
            if (it != null) {
                value = it.copy(
                    userSex = it.userSex ?: value?.userSex,
                    userHeight = it.userHeight ?: value?.userHeight,
                    userWeight = it.userWeight ?: value?.userWeight,
                    userAge = it.userAge ?: value?.userAge,
                    userVo2max = it.userVo2max ?: value?.userVo2max,
                    userRestingHeartRate = it.userRestingHeartRate ?: value?.userRestingHeartRate,
                    userMaxHeartRate = it.userMaxHeartRate ?: value?.userMaxHeartRate,
                )
            }
        }

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