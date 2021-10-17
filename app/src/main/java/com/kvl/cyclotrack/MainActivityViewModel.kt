package com.kvl.cyclotrack

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    tripsRepository: TripsRepository,
) : ViewModel() {
    val latestTrip = tripsRepository.observeNewest()
}