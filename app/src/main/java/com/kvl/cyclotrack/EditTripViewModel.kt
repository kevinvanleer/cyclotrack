package com.kvl.cyclotrack

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditTripViewModel @Inject constructor(
    private val tripsRepository: TripsRepository,
    private val bikesRepository: BikeRepository
) : ViewModel(
) {
    val TAG = "EditTripVm"
    lateinit var tripInfo: Trip
    fun setTrip(tripId: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            tripInfo = tripsRepository.get(tripId)
        }

    fun observeBikes() = bikesRepository.observeAll()

    fun updateTripName(value: String) {
        try {
            changeDetails(value, tripInfo.notes, tripInfo.userWheelCircumference)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update trip with user edits", e)
        }
    }

    fun updateTripNotes(value: String) {
        try {
            changeDetails(tripInfo.name!!, value, tripInfo.userWheelCircumference)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update trip with user edits", e)
        }
    }

    fun updateTripCircumference(value: String) {
        try {
            changeDetails(tripInfo.name!!, tripInfo.notes, userCircumferenceToMeters(value))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update trip with user edits", e)
        }
    }

    fun updateTripBikeId(bikeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            tripsRepository.updateBikeId(tripInfo.id!!, bikeId)
        }
    }

    private fun changeDetails(name: String, notes: String?, circumference: Float?) =
        viewModelScope.launch(Dispatchers.IO) {
            if (tripsRepository.get(tripInfo.id!!).googleFitSyncStatus == GoogleFitSyncStatusEnum.SYNCED) {
                tripsRepository.setGoogleFitSyncStatus(tripInfo.id!!, GoogleFitSyncStatusEnum.DIRTY)
            }
            tripsRepository.updateTripStuff(TripStuff(tripInfo.id!!, name, notes, circumference))
            tripInfo = tripsRepository.get(tripInfo.id!!)
        }
}