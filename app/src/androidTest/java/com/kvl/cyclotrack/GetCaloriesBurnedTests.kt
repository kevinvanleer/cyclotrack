package com.kvl.cyclotrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GetCaloriesBurnedTests {
    @Test
    fun getCaloriesBurned_success() {
        val testTrip = Trip(userSex = UserSexEnum.MALE,
            userAge = 40f,
            userWeight = 86f,
            userHeight = 1.82f,
            userVo2max = 51f,
            duration = 1800.0)
        val heartRate = 160.toShort()
        val testPrefs = MockSharedPreference()
        testPrefs.edit().apply() {
            this.putString(CyclotrackApp.instance.getString(R.string.preference_key_biometrics_user_sex),
                "MALE")
            this.putString(CyclotrackApp.instance.getString(R.string.preference_key_biometrics_user_age),
                "40")
            this.putString(CyclotrackApp.instance.getString(R.string.preference_key_biometrics_user_weight),
                "190")
            this.putString(CyclotrackApp.instance.getString(R.string.preference_key_biometrics_user_height),
                "72")
            this.putString(CyclotrackApp.instance.getString(R.string.preference_key_biometrics_user_vo2max),
                "50")
        }
        assertEquals(600, getCaloriesBurned(testTrip, heartRate, testPrefs))
    }
}