package com.kvl.cyclotrack

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

const val POUNDS_TO_KG = 0.453592

fun convertSystemToUserMass(mass: Float, context: Context) =
    mass * when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "1")) {
        "1" -> 1 / POUNDS_TO_KG
        else -> 1.0
    }

fun convertSystemToUserHeight(height: Float, context: Context) =
    height * when (PreferenceManager.getDefaultSharedPreferences(context)
        .getString("display_units", "1")) {
        "1" -> METERS_TO_FEET * FEET_TO_INCHES
        "2" -> 1e2
        else -> 1.0
    }

fun getMassConversionFactor(sharedPreferences: SharedPreferences) =
    when (sharedPreferences.getString("display_units", "1")) {
        "1" -> POUNDS_TO_KG
        else -> 1.0
    }

fun dateStringToAge(dateString: String) =
    try {
        SimpleDateFormat(CyclotrackApp.instance.getString(R.string.date_format_patten_dob),
            Locale.US).parse(dateString
        )
            ?.let { dateObj -> (System.currentTimeMillis() - dateObj.time) / 1000f / 3600f / 24f / 365f }
    } catch (e: ParseException) {
        null
    }

fun getBiometrics(id: Long, context: Context) = Biometrics(
    id,
    getUserSex(context),
    getUserWeight(context),
    getUserHeight(context),
    getUserAge(context),
    getUserVo2max(context),
    getUserRestingHeartRate(context),
    getUserMaxHeartRate(context),
)

fun getBiometrics(id: Long, sharedPreferences: SharedPreferences) = Biometrics(
    id,
    getUserSex(sharedPreferences),
    getUserWeight(sharedPreferences),
    getUserHeight(sharedPreferences),
    getUserAge(sharedPreferences),
    getUserVo2max(sharedPreferences),
    getUserRestingHeartRate(sharedPreferences),
    getUserMaxHeartRate(sharedPreferences),
)

fun getUserSex(context: Context) =
    try {
        PreferenceManager.getDefaultSharedPreferences(context).getString(context
            .getString(R.string.preference_key_biometrics_user_sex),
            "")?.let {
            UserSexEnum.valueOf(it)
        }
    } catch (e: IllegalArgumentException) {
        null
    }

fun getUserWeight(context: Context): Float? =
    PreferenceManager.getDefaultSharedPreferences(context).getString(context
        .getString(R.string.preference_key_biometrics_user_weight),
        "")?.toFloatOrNull()?.let {
        getMassConversionFactor(PreferenceManager.getDefaultSharedPreferences(context)) * it

    }?.toFloat()

fun getUserHeight(context: Context): Float? =
    PreferenceManager.getDefaultSharedPreferences(context).getString(context
        .getString(R.string.preference_key_biometrics_user_height),
        "")?.toFloatOrNull()?.let {
        when (PreferenceManager.getDefaultSharedPreferences(context)
            .getString("display_units", "1")) {
            "1" -> INCHES_TO_FEET * FEET_TO_METERS
            "2" -> 0.01
            else -> 1.0
        } * it
    }?.toFloat()

fun getUserDob(sharedPreferences: SharedPreferences): String? =
    try {
        sharedPreferences.getString(CyclotrackApp.instance
            .getString(R.string.preference_key_biometrics_user_dob),
            "")
    } catch (e: ParseException) {
        null
    }

fun getUserDob(context: Context): String? =
    try {
        PreferenceManager.getDefaultSharedPreferences(context).getString(
            context
                .getString(R.string.preference_key_biometrics_user_dob),
            "")
    } catch (e: ParseException) {
        null
    }

fun getUserAge(context: Context): Float? =
    try {
        PreferenceManager.getDefaultSharedPreferences(context).getString(
            context
                .getString(R.string.preference_key_biometrics_user_dob),
            "")?.let { dateString ->
            dateStringToAge(dateString)
        }
    } catch (e: ParseException) {
        null
    }

fun getUserVo2max(context: Context) =
    PreferenceManager.getDefaultSharedPreferences(context).getString(context
        .getString(R.string.preference_key_biometrics_user_vo2max),
        "")?.toFloatOrNull()

fun getUserRestingHeartRate(context: Context) =
    PreferenceManager.getDefaultSharedPreferences(context).getString(context
        .getString(R.string.preference_key_biometrics_user_restingHeartRate),
        "")?.toIntOrNull()

fun getUserMaxHeartRate(context: Context) =
    PreferenceManager.getDefaultSharedPreferences(context).getString(context
        .getString(R.string.preference_key_biometrics_user_maxHeartRate),
        "")?.toIntOrNull()

fun getUserSex(sharedPreferences: SharedPreferences) =
    try {
        sharedPreferences.getString(CyclotrackApp.instance
            .getString(R.string.preference_key_biometrics_user_sex),
            "")?.let {
            UserSexEnum.valueOf(it)
        }
    } catch (e: IllegalArgumentException) {
        null
    }

fun getUserWeight(sharedPreferences: SharedPreferences): Float? =
    sharedPreferences.getString(CyclotrackApp.instance
        .getString(R.string.preference_key_biometrics_user_weight),
        "")?.toFloatOrNull()?.let {
        getMassConversionFactor(sharedPreferences) * it

    }?.toFloat()

fun getUserHeight(sharedPreferences: SharedPreferences): Float? =
    sharedPreferences.getString(CyclotrackApp.instance
        .getString(R.string.preference_key_biometrics_user_height),
        "")?.toFloatOrNull()?.let {
        when (sharedPreferences.getString("display_units", "1")) {
            "1" -> INCHES_TO_FEET * FEET_TO_METERS
            "2" -> 0.001
            else -> 1.0
        } * it

    }?.toFloat()

fun getUserAge(sharedPreferences: SharedPreferences): Float? =
    try {
        sharedPreferences.getString(
            CyclotrackApp.instance
                .getString(R.string.preference_key_biometrics_user_dob),
            "")?.let { dateString ->
            dateStringToAge(dateString)
        }
    } catch (e: ParseException) {
        null
    }

fun getUserVo2max(sharedPreferences: SharedPreferences) =
    sharedPreferences.getString(CyclotrackApp.instance
        .getString(R.string.preference_key_biometrics_user_vo2max),
        "")?.toFloatOrNull()

fun getUserRestingHeartRate(sharedPreferences: SharedPreferences) =
    sharedPreferences.getString(CyclotrackApp.instance
        .getString(R.string.preference_key_biometrics_user_restingHeartRate),
        "")?.toIntOrNull()

fun getUserMaxHeartRate(sharedPreferences: SharedPreferences) =
    sharedPreferences.getString(CyclotrackApp.instance
        .getString(R.string.preference_key_biometrics_user_maxHeartRate),
        "")?.toIntOrNull()

fun estimateMaxHeartRate(age: Int) = (208 - 0.7 * age).toInt()

fun estimateVo2Max(restingHeartRate: Int, maximumHeartRate: Int): Float {
    //http://www.shapesense.com/fitness-exercise/calculators/vo2max-calculator.shtml
    return (15.3 * maximumHeartRate / restingHeartRate).toFloat()
}

fun getCaloriesEstimateType(
    sharedPrefs: SharedPreferences,
): String {
    fun getSex(value: UserSexEnum?) = value?.name ?: getUserSex(sharedPrefs)?.name
    fun getAge(value: Float?) = (value ?: getUserAge(sharedPrefs))?.roundToInt()
    fun getWeight(value: Float?) = value ?: getUserWeight(sharedPrefs)
    fun getHeight(value: Float?) = value ?: getUserHeight(sharedPrefs)
    fun getVo2max(value: Float?) = value ?: getUserVo2max(sharedPrefs)
    fun getRestingHeartRate(value: Int?) = value ?: getUserRestingHeartRate(sharedPrefs)

    var label =
        CyclotrackApp.instance.getString(R.string.biometric_preferences_no_data_hint)

    val canGetVo2max = getVo2max(null) != null || getRestingHeartRate(null) != null

    if (getWeight(null) != null) {
        label =
            CyclotrackApp.instance.getString(R.string.biometrics_preferences_weight_only_hint)
        if (canGetVo2max &&
            getSex(null) != null &&
            getAge(null) != null
        ) {
            label =
                CyclotrackApp.instance.getString(R.string.biometrics_preferences_gross_calories_hint)
            if (getHeight(null) != null) {
                label =
                    CyclotrackApp.instance.getString(R.string.biometric_resources_net_calories_hint)
            }
        }
    }

    return label
}

fun getCaloriesBurnedLabel(
    biometrics: Biometrics,
    overview: Trip,
    heartRate: Short?,
): String {

    var label =
        "Calories (${speedToMets((overview.distance!! / overview.duration!!).toFloat())} METs)"

    val estVo2max = biometrics.userVo2max
        ?: biometrics.userRestingHeartRate?.let {
            estimateVo2Max(it,
                biometrics.userMaxHeartRate
                    ?: estimateMaxHeartRate(biometrics.userAge?.roundToInt()!!))
        }

    if (biometrics.userWeight != null) {
        if (heartRate != null) {
            if (estVo2max != null &&
                biometrics.userSex != null &&
                biometrics.userAge != null
            ) {
                label = "Calories (gross)"
                if (biometrics.userHeight != null) {
                    label = "Calories (net)"
                }
            }
        }
    }

    return label
}

fun getCaloriesBurnedLabel(
    overview: Trip,
    heartRate: Short?,
    sharedPrefs: SharedPreferences,
): String {
    fun getSex(value: UserSexEnum?) = value ?: getUserSex(sharedPrefs)
    fun getAge(value: Float?) = (value ?: getUserAge(sharedPrefs))
    fun getWeight(value: Float?) = value ?: getUserWeight(sharedPrefs)
    fun getHeight(value: Float?) = value ?: getUserHeight(sharedPrefs)
    fun getVo2max(value: Float?) = value ?: getUserVo2max(sharedPrefs)
    fun getRestingHeartRate(value: Int?) = value ?: getUserRestingHeartRate(sharedPrefs)
    fun getMaxHeartRate(value: Int?) = value ?: getUserMaxHeartRate(sharedPrefs)

    return getCaloriesBurnedLabel(Biometrics(
        0,
        userAge = getAge(overview.userAge),
        userSex = getSex(overview.userSex),
        userWeight = getWeight(overview.userWeight),
        userHeight = getHeight(overview.userHeight),
        userVo2max = getVo2max(overview.userVo2max),
        userRestingHeartRate = getRestingHeartRate(overview.userRestingHeartRate),
        userMaxHeartRate = getMaxHeartRate(overview.userMaxHeartRate),
    ), overview, heartRate)
}

fun getCaloriesBurned(
    biometrics: Biometrics,
    overview: Trip,
    heartRate: Short?,
): Int? {

    return estimateCaloriesBurned(biometrics.userSex?.name,
        biometrics.userAge?.roundToInt(),
        biometrics.userWeight!!,
        biometrics.userHeight,
        biometrics.userVo2max,
        biometrics.userRestingHeartRate,
        biometrics.userMaxHeartRate,
        overview.duration?.toFloat()!!,
        overview.distance?.toFloat()!!,
        heartRate)
}

fun estimateCaloriesBurned(
    sex: String?,
    age: Int?,
    weight: Float,
    height: Float?,
    vo2Max: Float?,
    restingHeartRate: Int?,
    maximumHeartRate: Int?,
    duration: Float,
    distance: Float,
    heartRate: Short?,
): Int? {
    val estVo2max = vo2Max ?: restingHeartRate?.let {
        estimateVo2Max(it,
            maximumHeartRate ?: age!!)
    }
    return if (sex != null && age != null && heartRate != null && estVo2max != null) {
        if (height != null) estimateNetCaloriesBurned(sex,
            age,
            weight,
            height,
            estVo2max,
            duration,
            heartRate) else estimateGrossCaloriesBurned(sex,
            age,
            weight,
            estVo2max,
            duration,
            heartRate)
    } else estimateCaloriesBurnedMets(weight, distance, duration)
}

fun estimateCaloriesBurnedMets(
    bodyMass: Float,
    distance: Float,
    duration: Float,
): Int {
    return estimateCaloriesBurnedMets(speedToMets(distance / duration), bodyMass, (duration / 60))
}

//https://www.cmsfitnesscourses.co.uk/blog/using-mets-to-calculate-calories-burned/
//mets * 3.5 * mass (kg) / 200 * duration (in minutes)
fun estimateCaloriesBurnedMets(mets: Int, bodyMass: Float, duration: Float) =
    (mets * 3.5 * bodyMass * 0.005 * duration).toInt()

fun speedToMets(averageSpeed: Float): Int {
    //https://community.plu.edu/~chasega/met.html
    return when (averageSpeed) {
        in 0.0..4.47 -> 4
        in 4.47..5.36 -> 6
        in 5.36..6.26 -> 8
        in 6.26..7.15 -> 10
        in 7.15..8.94 -> 12
        else -> 16
    }
}

fun estimateNetCaloriesBurned(
    sex: String,
    age: Int,
    weight: Float,
    height: Float,
    vo2Max: Float,
    duration: Float,
    heartRate: Short,
): Int {
    return (estimateGrossCaloriesBurned(sex,
        age,
        weight,
        vo2Max,
        duration,
        heartRate) - (estimateBasalMetabolicRate(sex,
        weight,
        height,
        age).toFloat() / (24 * 3600) * duration)).toInt()
}

fun estimateGrossCaloriesBurned(
    sex: String,
    age: Int,
    weight: Float,
    vo2Max: Float,
    duration: Float,
    heartRate: Short,
): Int {
    //http://www.shapesense.com/fitness-exercise/calculators/heart-rate-based-calorie-burn-calculator.shtml
    return when (sex) {
        "MALE" -> ((-95.7735 + (0.634 * heartRate) + (0.404 * vo2Max) + (0.394 * weight) + (0.271 * age)) / 4.184) * (duration / 60)
        else -> ((-59.3954 + (0.45 * heartRate) + (0.380 * vo2Max) + (0.103 * weight) + (0.274 * age)) / 4.184) * (duration / 60)
    }.roundToInt()
}

fun estimateBasalMetabolicRate(sex: String, weight: Float, height: Float, age: Int): Int {
    fun harrisBenedict(): Int {
        //https://blog.nasm.org/nutrition/resting-metabolic-rate-how-to-calculate-and-improve-yours
        //Harris and Benedict
        //Men: 88.362 + (13.397 × weight in kg) + (4.799 × height in cm) - (5.677 × age in years)
        //Women: 447.593 + (9.247 × weight in kg) + (3.098 × height in cm) - (4.330 × age in years)
        return when (sex) {
            "MALE" -> (88.362 + (13.397 * weight) + (479.9 * height) - (5.677 * age)).roundToInt()
            else -> (447.593 + (9.247 * weight) + (309.8 * height) - (4.330 * age)).roundToInt()
        }
    }

    fun mifflinStJeor(): Int {
        //https://blog.nasm.org/nutrition/resting-metabolic-rate-how-to-calculate-and-improve-yours
        //Mifflin-St Jeor
        //Men: (10 × weight in kg) + (6.25 × height in cm) - (5 × age in years) + 5
        //Women: (10 × weight in kg) + (6.25 × height in cm) - (5 × age in years) - 161
        return when (sex) {
            "MALE" -> ((10 * weight) + (625 * height) - (5 * age) + 5).roundToInt()
            else -> ((10 * weight) + (625 * height) - (5 * age) - 161).roundToInt()
        }
    }

    fun shapesense(): Int {
        //http://www.shapesense.com/fitness-exercise/calculators/bmr-calculator.aspx
        return when (sex) {
            "MALE" -> (13.75 * weight) + (500 * height) - (6.76 * age) + 66
            else -> (9.56 * weight) + (185 * height) - (4.68 * age) + 655
        }.roundToInt()
    }
    //return shapesense()
    return mifflinStJeor()
    //return harrisBenedict()
}
