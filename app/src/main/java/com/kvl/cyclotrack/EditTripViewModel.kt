package com.kvl.cyclotrack

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EditTripViewModel @ViewModelInject constructor(
    private val tripsRepository: TripsRepository,
) : ViewModel(
) {
    lateinit var tripInfo: Trip
    fun setTrip(tripId: Long) =
        viewModelScope.launch(Dispatchers.IO) {
            tripInfo = tripsRepository.getTripOnce(tripId)
        }

    fun changeDetails(name: String, notes: String) =
        viewModelScope.launch(Dispatchers.IO) {
            tripsRepository.updateTripStuff(TripStuff(tripInfo.id!!, name, notes))
            tripInfo = tripsRepository.getTripOnce(tripInfo.id!!)
        }
}