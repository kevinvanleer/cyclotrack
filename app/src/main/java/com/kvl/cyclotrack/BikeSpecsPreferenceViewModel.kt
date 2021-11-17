package com.kvl.cyclotrack

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.databinding.Bindable
import androidx.databinding.Observable
import androidx.databinding.PropertyChangeRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class BikeSpecsPreferenceViewModel @Inject constructor(
    private val bikesRepository: BikeRepository,
    private val sharedPreferences: SharedPreferences,
) : ViewModel(), Observable {
    private val logTag = "BikeSpecsViewModel"

    private val callbacks: PropertyChangeRegistry = PropertyChangeRegistry()

    override fun addOnPropertyChangedCallback(
        callback: Observable.OnPropertyChangedCallback
    ) {
        callbacks.add(callback)
    }

    override fun removeOnPropertyChangedCallback(
        callback: Observable.OnPropertyChangedCallback
    ) {
        callbacks.remove(callback)
    }

    private fun notifyChange() {
        callbacks.notifyCallbacks(this, 0, null)
    }

    var currentBikeId: Long = 1
        set(newValue) {
            field = newValue
            notifyChange()
        }

    val bikes = bikesRepository.observeAll()

    var circumference
        @get:Bindable
        get() =
            bikes.value?.find { bike -> bike.id == currentBikeId }?.wheelCircumference.toString()
        set(newValue) {
            bikes.value?.let { bikeList ->
                viewModelScope.launch(Dispatchers.IO) {
                    bikesRepository.update(
                        bikeList.find { bike -> bike.id == currentBikeId }!!
                            .copy(wheelCircumference = newValue.toFloat())
                    )
                }
            }
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
        @get:Bindable
        get() =
            bikes.value?.find { bike -> bike.id == currentBikeId }?.weight.toString()
        set(newValue) {
            bikes.value?.let { bikeList ->
                viewModelScope.launch(Dispatchers.IO) {
                    bikesRepository.update(
                        bikeList.find { bike -> bike.id == currentBikeId }!!
                            .copy(weight = newValue.toFloatOrNull())
                    )
                }
            }
        }

    fun getPurchaseDateInstant(): Instant? = try {
        bikes.value?.find { bike -> bike.id == currentBikeId }?.dateOfPurchase?.let {
            Instant.ofEpochSecond(it)
        }!!
    } catch (e: Exception) {
        null
    }

    fun setPurchaseDateInstant(newValue: Instant) {
        bikes.value?.let { bikeList ->
            viewModelScope.launch(Dispatchers.IO) {
                bikesRepository.update(
                    bikeList.find { bike -> bike.id == currentBikeId }!!
                        .copy(dateOfPurchase = newValue.epochSecond)
                )
            }
        }
    }

    var purchaseDate: String = "0000-00-00"
        @get:Bindable
        get() =
            try {
                bikes.value?.find { bike -> bike.id == currentBikeId }?.dateOfPurchase?.let {
                    Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(
                        DateTimeFormatter.ISO_LOCAL_DATE
                    )
                }!!
            } catch (e: Exception) {
                ""
            }
        /*set(newValue) {
            bikes.value?.let { bikeList ->
                viewModelScope.launch(Dispatchers.IO) {
                    bikesRepository.update(
                        bikeList.find { bike -> bike.id == currentBikeId }!!
                            .copy(
                                dateOfPurchase = LocalDate.parse(
                                    newValue,
                                    DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault())
                                ).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
                            )
                    )
                }
            }
        }*/
        private set

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