package com.kvl.cyclotrack

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.google.android.gms.common.api.ApiException
import com.kvl.cyclotrack.util.MAX_MAX_HEART_RATE
import com.kvl.cyclotrack.util.MAX_RESTING_HEART_RATE
import com.kvl.cyclotrack.util.MAX_VO2MAX
import com.kvl.cyclotrack.util.convertSystemToUserHeight
import com.kvl.cyclotrack.util.convertSystemToUserMass
import com.kvl.cyclotrack.util.dateStringToAge
import com.kvl.cyclotrack.util.estimateMaxHeartRate
import com.kvl.cyclotrack.util.estimateVo2Max
import com.kvl.cyclotrack.util.getCaloriesEstimateType
import com.kvl.cyclotrack.util.getMaxUserHeight
import com.kvl.cyclotrack.util.getMaxUserWeight
import com.kvl.cyclotrack.util.getUserAge
import com.kvl.cyclotrack.util.getUserMaxHeartRate
import com.kvl.cyclotrack.util.getUserRestingHeartRate
import com.kvl.cyclotrack.util.getUserVo2max
import com.kvl.cyclotrack.util.parseBiometric
import kotlin.math.roundToInt
import kotlin.reflect.KProperty

class BiometricsViewModel constructor(
    private val googleFitApiService: GoogleFitApiService,
    private val sharedPreferences: SharedPreferences,
) : BaseObservable() {
    private val logTag = "BIOMETRICS_VM"

    private val keyUserSex =
        CyclotrackApp.instance.getString(R.string.preference_key_biometrics_user_sex)

    // Holds the typed text so an invalid entry stays visible without being saved
    // or replacing the stored value.
    class ObservablePreference constructor(
        resId: Int,
        private val sharedPreferences: SharedPreferences,
        private val observable: BaseObservable,
        private val errorMessage: () -> String,
        private val isValid: (String) -> Boolean,
    ) {
        private val prefKey = CyclotrackApp.instance.getString(resId)
        private var text: String? = null

        var value: String?
            get() = text ?: sharedPreferences.getString(prefKey, "")
            set(newValue) {
                text = newValue
                when {
                    newValue.isNullOrBlank() -> sharedPreferences.edit { remove(prefKey) }
                    isValid(newValue) -> sharedPreferences.edit { putString(prefKey, newValue) }
                }
                observable.notifyChange()
            }

        val error
            get() = value?.takeIf { it.isNotBlank() && !isValid(it) }?.let { errorMessage() }

        operator fun getValue(
            biometricsViewModel: BiometricsViewModel,
            property: KProperty<*>,
        ) = value

        operator fun setValue(
            biometricsViewModel: BiometricsViewModel,
            property: KProperty<*>,
            newValue: String?,
        ) {
            value = newValue
        }
    }

    private fun makeObservablePreference(resId: Int, max: () -> Int) =
        ObservablePreference(
            resId,
            sharedPreferences,
            this,
            { "Must be above 0 and at most ${max()}" },
        ) { parseBiometric(it, max()) != null }

    private val dobPreference = ObservablePreference(
        R.string.preference_key_biometrics_user_dob,
        sharedPreferences,
        this,
        { "Enter a valid date (yyyy-MM-dd)" },
    ) { dateStringToAge(it) != null }
    private val vo2maxPreference =
        makeObservablePreference(R.string.preference_key_biometrics_user_vo2max) { MAX_VO2MAX }
    private val maxHeartRatePreference =
        makeObservablePreference(R.string.preference_key_biometrics_user_maxHeartRate) { MAX_MAX_HEART_RATE }
    private val restingHeartRatePreference =
        makeObservablePreference(R.string.preference_key_biometrics_user_restingHeartRate) { MAX_RESTING_HEART_RATE }
    private val weightPreference =
        makeObservablePreference(R.string.preference_key_biometrics_user_weight) { getMaxUserWeight(sharedPreferences) }
    private val heightPreference =
        makeObservablePreference(R.string.preference_key_biometrics_user_height) { getMaxUserHeight(sharedPreferences) }

    var sex: Int
        @Bindable
        get() =
            try {
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
        set(newValue) {
            Log.d(logTag, "Sex $newValue")
            sharedPreferences.edit {
                this.putString(
                    keyUserSex, when (newValue) {
                        R.id.preference_biometrics_sex_male -> UserSexEnum.MALE.name
                        R.id.preference_biometrics_sex_female -> UserSexEnum.FEMALE.name
                        else -> null
                    }
                )
            }
        }


    @get:Bindable
    var dob by dobPreference

    @get:Bindable
    var vo2max by vo2maxPreference

    @get:Bindable
    var maxHeartRate by maxHeartRatePreference

    @get:Bindable
    val dobError get() = dobPreference.error

    @get:Bindable
    val vo2maxError get() = vo2maxPreference.error

    @get:Bindable
    val maxHeartRateError get() = maxHeartRatePreference.error

    @get:Bindable
    val restingHeartRateError get() = restingHeartRatePreference.error.takeIf { isRestingHeartRateEditable }

    @get:Bindable
    val weightError get() = weightPreference.error.takeIf { isWeightEditable }

    @get:Bindable
    val heightError get() = heightPreference.error.takeIf { isHeightEditable }

    var weight
        @Bindable
        get() =
            try {
                when (gfWeight != null && useGoogleFitBiometrics) {
                    true -> "%.1f".format(
                        convertSystemToUserMass(
                            gfWeight!!,
                            CyclotrackApp.instance
                        )
                    )

                    else -> weightPreference.value
                }
            } catch (e: ClassCastException) {
                ""
            }
        set(newValue) {
            if (gfWeight == null || !useGoogleFitBiometrics) {
                weightPreference.value = newValue
            }
        }

    var height
        @Bindable
        get() =
            try {
                when (gfHeight != null && useGoogleFitBiometrics) {
                    true -> "%.1f".format(
                        convertSystemToUserHeight(
                            gfHeight!!,
                            CyclotrackApp.instance
                        )
                    )

                    else -> heightPreference.value
                }
            } catch (e: ClassCastException) {
                ""
            }
        set(newValue) {
            if (gfHeight == null || !useGoogleFitBiometrics) {
                heightPreference.value = newValue
            }
        }

    var restingHeartRate
        @Bindable
        get() =
            try {
                when (!isRestingHeartRateEditable) {
                    true -> gfRestingHr.toString()
                    else -> restingHeartRatePreference.value
                }
            } catch (e: ClassCastException) {
                ""
            }
        set(newValue) {
            if (isRestingHeartRateEditable) {
                restingHeartRatePreference.value = newValue
            }
        }

    var useGoogleFitBiometrics: Boolean
        @Bindable
        get() =
            try {
                sharedPreferences.getBoolean(
                    CyclotrackApp.instance.getString(R.string.preference_key_biometrics_use_google_fit_biometrics),
                    true
                )
            } catch (e: ClassCastException) {
                true
            }
        set(newValue) {
            sharedPreferences.edit {
                this.putBoolean(
                    CyclotrackApp.instance.getString(R.string.preference_key_biometrics_use_google_fit_biometrics),
                    newValue
                )
            }
            notifyChange()
        }

    var shouldSyncGoogleFitBiometrics: Boolean
        @Bindable
        get() =
            try {
                sharedPreferences.getBoolean(
                    CyclotrackApp.instance.getString(R.string.preferences_key_advanced_enable_google_fit_sync_biometrics),
                    false
                )
            } catch (e: ClassCastException) {
                false
            }
        set(shouldSync) {
            sharedPreferences.edit {
                this.putBoolean(
                    CyclotrackApp.instance.getString(R.string.preferences_key_advanced_enable_google_fit_sync_biometrics),
                    shouldSync
                )
            }
            if (shouldSync) {
                gfHeight?.let {
                    height = "%.1f".format(
                        convertSystemToUserHeight(
                            it,
                            CyclotrackApp.instance
                        )
                    )
                }
                gfWeight?.let {
                    weight = "%.1f".format(
                        convertSystemToUserMass(
                            it,
                            CyclotrackApp.instance
                        )
                    )
                }
                gfRestingHr?.let {
                    restingHeartRate = gfRestingHr.toString()
                }
            }
            notifyChange()
        }

    var shouldUseGoogleFitRestingHeartRate: Boolean
        @Bindable
        get() =
            try {
                sharedPreferences.getBoolean(
                    CyclotrackApp.instance.getString(R.string.preferences_key_advanced_enable_google_fit_resting_heart_rate),
                    FeatureFlags.betaBuild
                )
            } catch (e: ClassCastException) {
                FeatureFlags.betaBuild
            }
        set(newValue) {
            sharedPreferences.edit {
                this.putBoolean(
                    CyclotrackApp.instance.getString(R.string.preferences_key_advanced_enable_google_fit_resting_heart_rate),
                    newValue
                )
            }
            notifyChange()
        }


    @get:Bindable
    val heightHint: String
        get() =
            when (sharedPreferences.getString(
                CyclotrackApp.instance.getString(R.string.preference_key_system_of_measurement),
                "1"
            )) {
                "1" -> "Height (inches)"
                else -> "Height (cm)"
            }

    @get:Bindable
    val weightHint: String
        get() =
            when (sharedPreferences.getString(
                CyclotrackApp.instance.getString(R.string.preference_key_system_of_measurement),
                "1"
            )) {
                "1" -> "Weight (lbs)"
                else -> "Weight (kg)"
            }

    @get:Bindable
    val vo2maxHint: String
        get() = "VO2Max ${
            (if (isRestingHeartRateEditable) getUserRestingHeartRate(sharedPreferences) else gfRestingHr)
                ?.takeIf { getUserVo2max(sharedPreferences) == null }
                ?.let { restingHr ->
                    (getUserMaxHeartRate(sharedPreferences)
                        ?: getUserAge(sharedPreferences, System.currentTimeMillis())
                            ?.roundToInt()?.let { estimateMaxHeartRate(it) })
                        ?.let { "(est ${estimateVo2Max(restingHr, it).toInt()} mL/kg/min)" }
                } ?: "(enter resting/max HR to estimate)"
        }"

    @get:Bindable
    val maxHrHint: String
        get() = "Max heart rate ${
            getUserAge(sharedPreferences, System.currentTimeMillis())?.let {
                "(${estimateMaxHeartRate(it.roundToInt())} based on age)"
            } ?: "(or use age to estimate)"
        }"

    @get:Bindable
    val isVo2maxDefined: Boolean
        get() = getUserVo2max(sharedPreferences) != null

    @get:Bindable
    val instructionText: String
        get() = getCaloriesEstimateType(sharedPreferences, System.currentTimeMillis())

    @get:Bindable
    val isEditable: Boolean
        get() = !googleFitApiService.hasPermission()

    @get:Bindable
    val googleFitEnabled: Boolean
        get() = googleFitApiService.hasPermission()


    var gfRestingHr: Int? = null
    var gfHeight: Float? = null
    var gfWeight: Float? = null

    @get:Bindable
    val isRestingHeartRateEditable
        get() = !useGoogleFitBiometrics || gfRestingHr == null || !shouldUseGoogleFitRestingHeartRate

    @get:Bindable
    val isWeightEditable
        get() = !useGoogleFitBiometrics || gfWeight == null

    @get:Bindable
    val isHeightEditable
        get() = !useGoogleFitBiometrics || gfHeight == null

    suspend fun updateGoogleFitBiometrics() {
        try {
            gfRestingHr = googleFitApiService.getLatestRestingHeartRate()
            gfHeight = googleFitApiService.getLatestHeight()
            gfWeight = googleFitApiService.getLatestWeight()
            notifyChange()
        } catch (e: ApiException) {
            Log.e(logTag, "Can't update biometrics", e)
        }
    }
}
