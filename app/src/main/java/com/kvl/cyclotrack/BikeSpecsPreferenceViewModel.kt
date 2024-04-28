package com.kvl.cyclotrack

import android.content.SharedPreferences
import androidx.databinding.Bindable
import androidx.databinding.Observable
import androidx.databinding.PropertyChangeRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
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
    private val tripsRepository: TripsRepository,
    private val bikesRepository: BikeRepository,
    private val sharedPreferences: SharedPreferences,
) : ViewModel(), Observable {
    private val logTag = "BikeSpecsViewModel"
    private val stringInitValue = "KVL_INITIALIZATION_VALUE"
    private lateinit var defaultBike: Bike
    private var notDefaultBikeMessage = "Another bike is currently the default"

    private val callbacks: PropertyChangeRegistry = PropertyChangeRegistry()

    init {
        viewModelScope.launch {
            bikesRepository.observeDefaultBike().asFlow().collect {
                defaultBike = it
                notDefaultBikeMessage =
                    "${defaultBike.name ?: "Bike ${defaultBike.id}"} is currently the default bike"
                if (currentBikeId == null) currentBikeId = defaultBike.id
            }
        }
    }

    fun addBike() {
        var newBikeId = currentBikeId
        viewModelScope.launch(Dispatchers.IO) {
            newBikeId = bikesRepository.add()
        }.invokeOnCompletion {
            reset()
            currentBikeId = newBikeId
        }
    }

    fun bikeHasTrips() = tripsRepository.getTripsForBike(currentBikeId ?: 1).isNotEmpty()

    fun deleteCurrentBike() =
        currentBikeId?.let { id ->
            viewModelScope.launch(Dispatchers.IO) {
                currentBikeId = null
                bikesRepository.delete(id)
            }.invokeOnCompletion {
                reset()
            }
        }

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

    fun notifyChange() {
        callbacks.notifyCallbacks(this, 0, null)
    }

    var currentBikeId: Long? = null
        set(newValue) {
            field = newValue
            notifyChange()
        }

    val bikes = bikesRepository.observeAll()

    fun reset() {
        name = stringInitValue
        circumference = stringInitValue
        bikeMass = stringInitValue
    }

    @get:Bindable
    var name: String? = stringInitValue
        get() = field.takeIf { it != stringInitValue }
            ?: bikes.value?.find { bike -> bike.id == currentBikeId }?.name ?: ""
        set(newValue) {
            if (newValue != stringInitValue) {
                bikes.value?.let { bikeList ->
                    viewModelScope.launch(Dispatchers.IO) {
                        bikeList.find { bike -> bike.id == currentBikeId }
                            ?.copy(name = newValue?.takeIf { it.isNotEmpty() })?.let {
                                bikesRepository.update(it)
                            }
                    }
                }
            }
            field = newValue
        }

    @get:Bindable
    var circumference: String = stringInitValue
        get() = field.takeIf { it != stringInitValue }
            ?: bikes.value?.find { bike -> bike.id == currentBikeId }?.wheelCircumference.toString()
                .takeUnless { it == "null" } ?: ""
        set(newValue) {
            if (newValue != stringInitValue) {
                bikes.value?.let { bikeList ->
                    viewModelScope.launch(Dispatchers.IO) {
                        bikeList.find { bike -> bike.id == currentBikeId }?.let {
                            bikesRepository.update(
                                it.copy(wheelCircumference = newValue.toFloatOrNull())
                            )
                        }
                    }
                }
            }
            field = newValue
        }

    @Bindable
    fun getIsDefaultBike() =
        bikes.value?.find { bike -> bike.id == currentBikeId }?.isDefault ?: false

    fun setIsDefaultBike(newValue: Boolean) {
        bikes.value?.let { bikeList ->
            viewModelScope.launch(Dispatchers.IO) {
                bikeList.find { bike -> bike.id == currentBikeId }?.let {
                    bikesRepository.update(
                        it.copy(isDefault = newValue)
                    )
                }
            }
        }
    }

    @get:Bindable
    var bikeMass: String? = stringInitValue
        get() = field.takeIf { it != stringInitValue }
            ?: bikes.value?.find { bike -> bike.id == currentBikeId }?.weight.toString()
                .takeUnless { it == "null" } ?: ""
        set(newValue) {
            if (newValue != stringInitValue) {
                bikes.value?.let { bikeList ->
                    viewModelScope.launch(Dispatchers.IO) {
                        bikeList.find { bike -> bike.id == currentBikeId }?.let {
                            bikesRepository.update(
                                it.copy(weight = newValue?.toFloatOrNull())
                            )
                        }
                    }
                }
            }
            field = newValue
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
                bikeList.find { bike -> bike.id == currentBikeId }
                    ?.copy(dateOfPurchase = newValue.epochSecond)
                    ?.let {
                        bikesRepository.update(it)
                    }
            }
        }
    }

    @get:Bindable
    var purchaseDate: String = "0000-00-00"
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
        private set

    val circumferenceHint: String
        get() = "Wheel circumference (in or mm)"

    val bikeMassHint: String
        get() =
            when (sharedPreferences.getString(
                CyclotrackApp.instance.getString(R.string.preference_key_system_of_measurement),
                "1"
            )) {
                "1" -> "Bike weight (lbs)"
                else -> "Bike weight (kg)"
            }

    val instructionText: String
        get() = "Cyclotrack uses your bike's specs along with sensor data to calculate speed and other metrics. BLE speed sensor required to utilize wheel circumference."

    val defaultBikeDescription: String
        get() = when (getIsDefaultBike()) {
            true -> "This bike will be used for new rides"
            else -> notDefaultBikeMessage
        }
}
