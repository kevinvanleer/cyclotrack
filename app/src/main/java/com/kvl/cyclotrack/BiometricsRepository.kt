package com.kvl.cyclotrack

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kvl.cyclotrack.util.getUserHeight
import com.kvl.cyclotrack.util.getUserWeight
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class BiometricsRepository @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val googleFitApiService: GoogleFitApiService,
) {

    val sex = sharedPreferences.getString(
        CyclotrackApp.instance.getString(
            R.string.preference_key_biometrics_user_sex
        ), ""
    )
    val dob = sharedPreferences.getString(
        CyclotrackApp.instance
            .getString(R.string.preference_key_biometrics_user_dob), ""
    )
    val maxHeartRate = sharedPreferences.getString(
        CyclotrackApp.instance
            .getString(R.string.preference_key_biometrics_user_maxHeartRate), ""
    )
    val vo2max = sharedPreferences.getString(
        CyclotrackApp.instance
            .getString(R.string.preference_key_biometrics_user_vo2max), ""
    )

    private val _restingHeartRate = MutableLiveData<String>(
        sharedPreferences.getString(
            CyclotrackApp.instance
                .getString(R.string.preference_key_biometrics_user_restingHeartRate), ""
        )
    )
    var restingHeartRate: LiveData<String>
        get() {
            runBlocking {
                launch {
                    googleFitApiService.getLatestRestingHeartRate()?.let {
                        _restingHeartRate.postValue(it.toString())
                    }
                }
            }
            return _restingHeartRate
        }
        set(newValue) {
            sharedPreferences.edit {
                this.putString(
                    CyclotrackApp.instance
                        .getString(R.string.preference_key_biometrics_user_restingHeartRate),
                    newValue.value
                )
            }
        }

    private val _height = MutableLiveData<Float>(getUserHeight(sharedPreferences))
    val height: LiveData<Float>
        get() {
            runBlocking {
                launch {
                    googleFitApiService.getLatestHeight()?.let {
                        _height.postValue(it)
                    }
                }
            }
            return _height
        }

    private val _weight = MutableLiveData<Float>(getUserWeight(sharedPreferences))
    val weight: LiveData<Float>
        get() {
            runBlocking {
                launch {
                    googleFitApiService.getLatestWeight()?.let {
                        _weight.postValue(it)
                    }
                }
            }
            return _weight
        }
}