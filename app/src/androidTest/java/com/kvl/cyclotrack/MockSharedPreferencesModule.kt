package com.kvl.cyclotrack

import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [SharedPreferencesModule::class]
)
object MockSharedPreferencesModule {
    @Provides

    @Singleton
    fun provideSharedPreferences(): SharedPreferences {
        return MockSharedPreference()
    }
}