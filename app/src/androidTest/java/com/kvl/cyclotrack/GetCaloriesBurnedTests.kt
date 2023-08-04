package com.kvl.cyclotrack

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.kvl.cyclotrack.util.getCaloriesBurned
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GetCaloriesBurnedTests {
    @Test
    fun getCaloriesBurned_success() {
        val testBiometrics = Biometrics(
            userSex = UserSexEnum.MALE,
            id = 0,
            userAge = 40f,
            userWeight = 86f,
            userHeight = 1.82f,
            userVo2max = 51f
        )
        val testTrip = Trip(
            userSex = UserSexEnum.MALE,
            userAge = 40f,
            userWeight = 86f,
            userHeight = 1.82f,
            userVo2max = 51f,
            duration = 1800.0,
            bikeId = 1
        )
        val heartRate = 160.toShort()
        val context = getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(
                context.getString(R.string.preference_key_biometrics_user_sex),
                "MALE"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_age),
                "40"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_weight),
                "190"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_height),
                "72"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_vo2max),
                "50"
            )
            putBoolean(
                context.getString(R.string.preferences_key_advanced_use_vo2max_calorie_estimate),
                false
            )
            commit()
        }
        assertEquals(331, getCaloriesBurned(context, testBiometrics, testTrip, heartRate))
    }

    @Test
    fun getCaloriesBurned_oldMethod() {
        val testBiometrics = Biometrics(
            userSex = UserSexEnum.MALE,
            id = 0,
            userAge = 40f,
            userWeight = 86f,
            userHeight = 1.82f,
            userVo2max = 51f
        )
        val testTrip = Trip(
            userSex = UserSexEnum.MALE,
            userAge = 40f,
            userWeight = 86f,
            userHeight = 1.82f,
            userVo2max = 51f,
            duration = 1800.0,
            bikeId = 1
        )
        val heartRate = 160.toShort()
        val context = getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(
                context.getString(R.string.preference_key_biometrics_user_sex),
                "MALE"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_age),
                "40"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_weight),
                "190"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_height),
                "72"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_vo2max),
                "50"
            )
            putBoolean(
                context.getString(R.string.preferences_key_advanced_use_vo2max_calorie_estimate),
                true
            )
            commit()
        }
        assertEquals(471, getCaloriesBurned(context, testBiometrics, testTrip, heartRate))
    }
}