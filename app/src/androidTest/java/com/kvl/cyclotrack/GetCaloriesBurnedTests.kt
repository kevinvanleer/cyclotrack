package com.kvl.cyclotrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GetCaloriesBurnedTests {
    @Test
    fun getCaloriesBurned_success() {
        val testBiometrics = Biometrics(userSex = UserSexEnum.MALE,
            id = 0,
            userAge = 40f,
            userWeight = 86f,
            userHeight = 1.82f,
            userVo2max = 51f)
        val testTrip = Trip(userSex = UserSexEnum.MALE,
            userAge = 40f,
            userWeight = 86f,
            userHeight = 1.82f,
            userVo2max = 51f,
            duration = 1800.0)
        val heartRate = 160.toShort()
        val testPrefs = MockSharedPreference()
        val context = getInstrumentation().targetContext
        testPrefs.edit().apply() {
            this.putString(context.getString(R.string.preference_key_biometrics_user_sex),
                "MALE")
            this.putString(context.getString(R.string.preference_key_biometrics_user_age),
                "40")
            this.putString(context.getString(R.string.preference_key_biometrics_user_weight),
                "190")
            this.putString(context.getString(R.string.preference_key_biometrics_user_height),
                "72")
            this.putString(context.getString(R.string.preference_key_biometrics_user_vo2max),
                "50")
        }
        //assertEquals(471, getCaloriesBurned(testTrip, heartRate, testPrefs))
        assertEquals(471, getCaloriesBurned(testBiometrics, testTrip, heartRate))
    }
}