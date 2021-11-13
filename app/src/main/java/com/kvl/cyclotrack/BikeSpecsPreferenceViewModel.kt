package com.kvl.cyclotrack

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.databinding.Bindable
import androidx.lifecycle.ViewModel
import com.kvl.cyclotrack.repos.BikeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BikeSpecsPreferenceViewModel @Inject constructor(
    private val bikesRepository: BikeRepository,
    private val sharedPreferences: SharedPreferences,
) : ViewModel() {
    private val logTag = "BikeSpecsViewModel"

    var circumference
        @Bindable
        get() =
            sharedPreferences.getString(
                CyclotrackApp.instance.getString(R.string.preference_key_wheel_circumference),
                ""
            )
        set(newValue) {
            sharedPreferences.edit {
                this.putString(
                    CyclotrackApp.instance.getString(R.string.preference_key_wheel_circumference),
                    newValue
                )
            }
        }

    var useAutoCircumference: Boolean
        @Bindable
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
        @Bindable
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
        @Bindable
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

    @get:Bindable
    val circumferenceHint: String
        get() = "Wheel circumference (in or mm)"

    @get:Bindable
    val bikeMassHint: String
        get() =
            when (sharedPreferences.getString(CyclotrackApp.instance.getString(R.string.preference_key_system_of_measurement),
                "1")) {
                "1" -> "Bike weight (lbs)"
                else -> "Bike weight (kg)"
            }

    @get:Bindable
    val instructionText: String
        get() = "Cyclotrack uses your bike's specs along with sensor data to calculate speed and other metrics. BLE speed sensor required to utilize wheel circumference."

}