package com.kvl.cyclotrack

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GetCaloriesBurnedLabelTest {
    @Test
    fun getCaloriesBurnedLabel_mets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(
                context.getString(R.string.preference_key_biometrics_user_weight),
                "190"
            )
            commit()
        }
        val testTrip = Trip(
            userWeight = 83f,
            distance = 30000.0,
            duration = 3600.0
        )
        Assert.assertEquals("Calories (12 METs)", getCaloriesBurnedLabel(context, testTrip, 168))
    }

    @Test
    fun getCaloriesBurnedLabel_net() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
            putString(
                context.getString(R.string.preference_key_biometrics_user_restingHeartRate),
                "60"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_maxHeartRate),
                "194"
            )
            putBoolean(
                context.getString(R.string.preferences_key_advanced_use_vo2max_calorie_estimate),
                true
            )
            commit()
        }
        val testTrip = Trip(
            userSex = UserSexEnum.MALE,
            userAge = 40f,
            userWeight = 83f,
            userHeight = 1.82f,
            userVo2max = 54f,
            userRestingHeartRate = 50,
            userMaxHeartRate = 200,
            distance = 30000.0,
            duration = 3600.0
        )
        Assert.assertEquals("Calories (net)", getCaloriesBurnedLabel(context, testTrip, 168))
    }

    @Test
    fun getCaloriesBurnedLabel_gross() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
                ""
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_vo2max),
                "50"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_restingHeartRate),
                "60"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_maxHeartRate),
                "194"
            )
            putBoolean(
                context.getString(R.string.preferences_key_advanced_use_vo2max_calorie_estimate),
                true
            )
            commit()
        }
        val testTrip = Trip(
            userSex = UserSexEnum.MALE,
            userAge = 40f,
            userWeight = 83f,
            userVo2max = 54f,
            userRestingHeartRate = 50,
            userMaxHeartRate = 200,
            distance = 30000.0,
            duration = 3600.0
        )
        Assert.assertEquals("Calories (gross)", getCaloriesBurnedLabel(context, testTrip, 168))
    }

    @Test
    fun getCaloriesBurnedLabel_cycling() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
                ""
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_vo2max),
                "50"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_restingHeartRate),
                "60"
            )
            putString(
                context.getString(R.string.preference_key_biometrics_user_maxHeartRate),
                "194"
            )
            putBoolean(
                context.getString(R.string.preferences_key_advanced_use_vo2max_calorie_estimate),
                false
            )
            commit()
        }
        val testTrip = Trip(
            userSex = UserSexEnum.MALE,
            userAge = 40f,
            userWeight = 83f,
            userVo2max = 54f,
            userRestingHeartRate = 50,
            userMaxHeartRate = 200,
            distance = 30000.0,
            duration = 3600.0
        )
        Assert.assertEquals("Calories (net)", getCaloriesBurnedLabel(context, testTrip, 168))
    }
}