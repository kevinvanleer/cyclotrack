package com.kvl.cyclotrack

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CyclotrackApp : Application() {
    companion object {
        lateinit var instance: CyclotrackApp private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}