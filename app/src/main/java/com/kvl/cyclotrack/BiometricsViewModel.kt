package com.kvl.cyclotrack

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.google.android.gms.common.api.ApiException
import kotlin.math.roundToInt
import kotlin.reflect.KProperty


class BiometricsViewModel constructor(
    private val googleFitApiService: GoogleFitApiService,
    private val sharedPreferences: SharedPreferences,
) : BaseObservable() {
    private val logTag = "BIOMETRICS_VM"

    private val keyUserSex =
        CyclotrackApp.instance.getString(R.string.preference_key_biometrics_user_sex)

    class ObservablePreference constructor(
        resId: Int,
        private val sharedPreferences: SharedPreferences,
        private val observable: BaseObservable,
    ) {
        private val prefKey = CyclotrackApp.instance.getString(resId)

        operator fun getValue(
            biometricsViewModel: BiometricsViewModel,
            property: KProperty<*>,
        ) = sharedPreferences.getString(prefKey, "")

        operator fun setValue(
            biometricsViewModel: BiometricsViewModel,
            property: KProperty<*>,
            newValue: String?,
        ) {
            sharedPreferences.edit {
                this.putString(prefKey, newValue)
            }
            observable.notifyPropertyChanged(BR.maxHrHint)
            observable.notifyPropertyChanged(BR.vo2maxHint)
            observable.notifyPropertyChanged(BR.vo2maxDefined)
            observable.notifyPropertyChanged(BR.instructionText)
        }
    }

    private fun makeObservablePreference(resId: Int) =
        ObservablePreference(resId, sharedPreferences, this)

    var sex: Int
        @Bindable
        get() {
            return try {
                sharedPreferences.getString(keyUserSex, "").let {
                    when (it) {
                        UserSexEnum.MALE.name -> R.id.preference_biometrics_sex_male
                        UserSexEnum.FEMALE.name -> R.id.preference_biometrics_sex_female
                        else -> -1
                    }
                }
            } catch (e: ClassCastException) {
                -1
            }
        }
        set(newValue) {
            Log.d(logTag, "Sex $newValue")
            sharedPreferences.edit {
                this.putString(keyUserSex, when (newValue) {
                    R.id.preference_biometrics_sex_male -> UserSexEnum.MALE.name
                    R.id.preference_biometrics_sex_female -> UserSexEnum.FEMALE.name
                    else -> null
                })
            }
        }

    @get:Bindable
    var weight by makeObservablePreference(R.string.preference_key_biometrics_user_weight)

    @get:Bindable
    var height by makeObservablePreference(R.string.preference_key_biometrics_user_height)

    @get:Bindable
    var dob by makeObservablePreference(R.string.preference_key_biometrics_user_dob)

    @get:Bindable
    var vo2max by makeObservablePreference(R.string.preference_key_biometrics_user_vo2max)

    @get:Bindable
    var restingHeartRate by makeObservablePreference(R.string.preference_key_biometrics_user_restingHeartRate)

    @get:Bindable
    var maxHeartRate by makeObservablePreference(R.string.preference_key_biometrics_user_maxHeartRate)

    @get:Bindable
    val heightHint: String
        get() =
            when (sharedPreferences.getString(CyclotrackApp.instance.getString(R.string.preference_key_system_of_measurement),
                "1")) {
                "1" -> "Height (inches)"
                else -> "Height (cm)"
            }

    @get:Bindable
    val weightHint: String
        get() =
            when (sharedPreferences.getString(CyclotrackApp.instance.getString(R.string.preference_key_system_of_measurement),
                "1")) {
                "1" -> "Weight (lbs)"
                else -> "Weight (kg)"
            }

    @get:Bindable
    val vo2maxHint: String
        get() = try {
            "VO2Max ${
                if (vo2max.isNullOrEmpty()) getUserRestingHeartRate(sharedPreferences)?.let {
                    estimateVo2Max(it,
                        getUserMaxHeartRate(sharedPreferences) ?: getUserAge(sharedPreferences)?.roundToInt()!!)
                }
                    ?.let { "(est ${it.toInt()} mL/kg/min)" } ?: "(enter resting/max HR to estimate)" else "(enter resting/max HR to estimate)"
            }"
        } catch (e: NullPointerException) {
            "VO2Max (enter resting/max HR to estimate)"
        }

    @get:Bindable
    val maxHrHint: String
        get() = "Max heart rate ${
            getUserAge(sharedPreferences)?.let {
                "(${estimateMaxHeartRate(it.roundToInt())} based on age)"
            } ?: "(or use age to estimate)"
        }"

    @get:Bindable
    val isVo2maxDefined: Boolean
        get() = getUserVo2max(sharedPreferences) != null

    @get:Bindable
    val instructionText: String
        get() = getCaloriesEstimateType(sharedPreferences)

    @get:Bindable
    val isEditable: Boolean
        get() = !googleFitApiService.hasPermission()

    var gfRestingHr: Int? = null
    var gfHeight: Float? = null
    var gfWeight: Float? = null

    @get:Bindable
    val isRestingHeartRateEditable
        get() = gfRestingHr == null

    @get:Bindable
    val isWeightEditable
        get() = gfWeight == null

    @get:Bindable
    val isHeightEditable
        get() = gfHeight == null

    suspend fun updateGoogleFitBiometrics() {
        try {
            gfRestingHr = googleFitApiService.getLatestRestingHeartRate()
            gfHeight = googleFitApiService.getLatestHeight()
            gfWeight = googleFitApiService.getLatestWeight()
            notifyPropertyChanged(BR.restingHeartRateEditable)
            notifyPropertyChanged(BR.heightEditable)
            notifyPropertyChanged(BR.weightEditable)
        } catch (e: ApiException) {
            Log.e(logTag, "Can't update biometrics", e)
        }
    }
}