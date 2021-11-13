package com.kvl.cyclotrack

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.databinding.Observable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BikeSpecsPreferenceViewModel @Inject constructor(
    private val bikesRepository: BikeRepository,
    private val sharedPreferences: SharedPreferences,
) : ViewModel(), Observable {
    private val logTag = "BikeSpecsViewModel"

    override fun addOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback?) {
        TODO("Not yet implemented")
    }

    override fun removeOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback?) {
        TODO("Not yet implemented")
    }

    var currentBikeId: Long = 1

    val bikes = bikesRepository.observeAll()

    var circumference
        get() =
            bikes.value?.find { bike -> bike.id == currentBikeId }?.wheelCircumference.toString()
        set(newValue) {
            bikesRepository.update(
                bikes.value!!.find { bike -> bike.id == currentBikeId }!!
                    .copy(wheelCircumference = newValue.toFloat())
            )
        }

    var useAutoCircumference: Boolean
        get() =
            try {
                sharedPreferences.getBoolean(CyclotrackApp.instance.getString(R.string.preference_key_useAutoCircumference),
                    true)
            } catch (e: ClassCastException) {
                true
            }
        set(newValue) {
            sharedPreferences.edit {
                this.putBoolean(
                    CyclotrackApp.instance.getString(R.string.preference_key_useAutoCircumference),
                    newValue
                )
            }
        }

    var bikeMass
        get() =
            sharedPreferences.getString(
                CyclotrackApp.instance.getString(R.string.preference_key_bike_mass),
                ""
            )
        set(newValue) {
            sharedPreferences.edit {
                this.putString(
                    CyclotrackApp.instance.getString(R.string.preference_key_bike_mass),
                    newValue
                )
            }
        }

    var purchaseDate
        get() =
            sharedPreferences.getString(
                CyclotrackApp.instance.getString(R.string.preference_key_bike_mass),
                ""
            )
        set(newValue) {
            sharedPreferences.edit {
                this.putString(
                    CyclotrackApp.instance.getString(R.string.preference_key_bike_mass),
                    newValue
                )
            }
        }

    val circumferenceHint: String
        get() = "Wheel circumference (in or mm)"

    val bikeMassHint: String
        get() =
            when (sharedPreferences.getString(CyclotrackApp.instance.getString(R.string.preference_key_system_of_measurement),
                "1")) {
                "1" -> "Bike weight (lbs)"
                else -> "Bike weight (kg)"
            }

    val instructionText: String
        get() = "Cyclotrack uses your bike's specs along with sensor data to calculate speed and other metrics. BLE speed sensor required to utilize wheel circumference."
}