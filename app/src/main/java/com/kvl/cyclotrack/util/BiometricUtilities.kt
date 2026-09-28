package com.kvl.cyclotrack.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.kvl.cyclotrack.Biometrics
import com.kvl.cyclotrack.CyclotrackApp
import com.kvl.cyclotrack.GoogleFitApiService
import com.kvl.cyclotrack.R
import com.kvl.cyclotrack.Trip
import com.kvl.cyclotrack.TripsRepository
import com.kvl.cyclotrack.UserSexEnum
import com.kvl.cyclotrack.data.CyclingPowerMeasurement
import com.kvl.cyclotrack.getWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

const val POUNDS_TO_KG = 0.453592
const val dateFormatPattenDob = "yyyy-MM-dd"


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

private const val MAX_AGE_YEARS = 183f

fun dateStringToAge(dateString: String, timestamp: Long = System.currentTimeMillis()) =
    try {
        SimpleDateFormat(
            dateFormatPattenDob,
            Locale.US
        ).parse(
            dateString
        )
            ?.let { dateObj -> (timestamp - dateObj.time) / 1000f / 3600f / 24f / 365f }
            ?.takeIf { it in 0f..MAX_AGE_YEARS }
    } catch (e: ParseException) {
        null
    }

fun getBiometrics(id: Long, context: Context, timestamp: Long) = Biometrics(
    id,
    getUserSex(context),
    getUserWeight(context),
    getUserHeight(context),
    getUserAge(context, timestamp),
    getUserVo2max(context),
    getUserRestingHeartRate(context),
    getUserMaxHeartRate(context),
)

fun getBiometrics(
    id: Long,
    sharedPreferences: SharedPreferences,
    timestamp: Long = System.currentTimeMillis()
) = Biometrics(
    id,
    getUserSex(sharedPreferences),
    getUserWeight(sharedPreferences),
    getUserHeight(sharedPreferences),
    getUserAge(sharedPreferences, timestamp),
    getUserVo2max(sharedPreferences),
    getUserRestingHeartRate(sharedPreferences),
    getUserMaxHeartRate(sharedPreferences),
)

fun getUserSex(context: Context) =
    try {
        PreferenceManager.getDefaultSharedPreferences(context).getString(
            context
                .getString(R.string.preference_key_biometrics_user_sex),
            ""
        )?.let {
            UserSexEnum.valueOf(it)
        }
    } catch (e: IllegalArgumentException) {
        null
    }

fun getUserWeight(context: Context): Float? =
    PreferenceManager.getDefaultSharedPreferences(context).getString(
        context
            .getString(R.string.preference_key_biometrics_user_weight),
        ""
    )?.toFloatOrNull()?.let {
        getMassConversionFactor(PreferenceManager.getDefaultSharedPreferences(context)) * it

    }?.toFloat()

fun getUserHeight(context: Context): Float? =
    PreferenceManager.getDefaultSharedPreferences(context).getString(
        context
            .getString(R.string.preference_key_biometrics_user_height),
        ""
    )?.toFloatOrNull()?.let {
        when (PreferenceManager.getDefaultSharedPreferences(context)
            .getString("display_units", "1")) {
            "1" -> INCHES_TO_FEET * FEET_TO_METERS
            "2" -> 0.01
            else -> 1.0
        } * it
    }?.toFloat()

fun getUserDob(sharedPreferences: SharedPreferences): String? =
    try {
        sharedPreferences.getString(
            CyclotrackApp.instance
                .getString(R.string.preference_key_biometrics_user_dob),
            ""
        )
    } catch (e: ParseException) {
        null
    }

fun getUserDob(context: Context): String? =
    try {
        PreferenceManager.getDefaultSharedPreferences(context).getString(
            context
                .getString(R.string.preference_key_biometrics_user_dob),
            ""
        )
    } catch (e: ParseException) {
        null
    }

fun getUserAge(context: Context, timestamp: Long): Float? =
    try {
        PreferenceManager.getDefaultSharedPreferences(context).getString(
            context
                .getString(R.string.preference_key_biometrics_user_dob),
            ""
        )?.let { dateString ->
            dateStringToAge(dateString, timestamp)
        }
    } catch (e: ParseException) {
        null
    }

fun getUserVo2max(context: Context) =
    PreferenceManager.getDefaultSharedPreferences(context).getString(
        context
            .getString(R.string.preference_key_biometrics_user_vo2max),
        ""
    )?.toFloatOrNull()

fun getUserRestingHeartRate(context: Context) =
    PreferenceManager.getDefaultSharedPreferences(context).getString(
        context
            .getString(R.string.preference_key_biometrics_user_restingHeartRate),
        ""
    )?.toIntOrNull()

fun getUserMaxHeartRate(context: Context) =
    PreferenceManager.getDefaultSharedPreferences(context).getString(
        context
            .getString(R.string.preference_key_biometrics_user_maxHeartRate),
        ""
    )?.toIntOrNull()

fun getUserSex(sharedPreferences: SharedPreferences) =
    try {
        sharedPreferences.getString(
            CyclotrackApp.instance
                .getString(R.string.preference_key_biometrics_user_sex),
            ""
        )?.let {
            UserSexEnum.valueOf(it)
        }
    } catch (e: IllegalArgumentException) {
        null
    }

fun getUserWeight(sharedPreferences: SharedPreferences): Float? =
    sharedPreferences.getString(
        CyclotrackApp.instance
            .getString(R.string.preference_key_biometrics_user_weight),
        ""
    )?.toFloatOrNull()?.let {
        getMassConversionFactor(sharedPreferences) * it

    }?.toFloat()

fun getUserHeight(sharedPreferences: SharedPreferences): Float? =
    sharedPreferences.getString(
        CyclotrackApp.instance
            .getString(R.string.preference_key_biometrics_user_height),
        ""
    )?.toFloatOrNull()?.let {
        when (sharedPreferences.getString("display_units", "1")) {
            "1" -> INCHES_TO_FEET * FEET_TO_METERS
            "2" -> 0.001
            else -> 1.0
        } * it

    }?.toFloat()

fun getUserAge(sharedPreferences: SharedPreferences, timestamp: Long): Float? =
    try {
        sharedPreferences.getString(
            CyclotrackApp.instance
                .getString(R.string.preference_key_biometrics_user_dob),
            ""
        )?.let { dateString ->
            dateStringToAge(dateString, timestamp)
        }
    } catch (e: ParseException) {
        null
    }

fun getUserVo2max(sharedPreferences: SharedPreferences) =
    sharedPreferences.getString(
        CyclotrackApp.instance
            .getString(R.string.preference_key_biometrics_user_vo2max),
        ""
    )?.toFloatOrNull()

fun getUserRestingHeartRate(sharedPreferences: SharedPreferences) =
    sharedPreferences.getString(
        CyclotrackApp.instance
            .getString(R.string.preference_key_biometrics_user_restingHeartRate),
        ""
    )?.toIntOrNull()

fun getUserMaxHeartRate(sharedPreferences: SharedPreferences) =
    sharedPreferences.getString(
        CyclotrackApp.instance
            .getString(R.string.preference_key_biometrics_user_maxHeartRate),
        ""
    )?.toIntOrNull()

fun estimateMaxHeartRate(age: Int) = (208 - 0.7 * age).toInt()

fun estimateVo2Max(restingHeartRate: Int, maximumHeartRate: Int): Float {
    //http://www.shapesense.com/fitness-exercise/calculators/vo2max-calculator.shtml
    return (15.3 * maximumHeartRate / restingHeartRate).toFloat()
}

fun getCaloriesEstimateType(
    sharedPrefs: SharedPreferences, timestamp: Long
): String {
    fun getSex(value: UserSexEnum?) = value?.name ?: getUserSex(sharedPrefs)?.name
    fun getAge(value: Float?) = (value ?: getUserAge(sharedPrefs, timestamp))?.roundToInt()
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
    context: Context,
    biometrics: Biometrics,
    overview: Trip,
    heartRate: Short?,
): String =
    (biometrics.userVo2max
        ?: (biometrics.userRestingHeartRate?.let { restingHr ->
            estimateVo2Max(
                restingHr,
                biometrics.userMaxHeartRate
                    ?: estimateMaxHeartRate(biometrics.userAge?.roundToInt()!!)
            )
        })).let { estVo2max ->
        "Calories" + if (biometrics.userSex != null && biometrics.userAge != null && heartRate != null && estVo2max != null) {
            if (biometrics.userHeight != null || !useVo2maxCalorieEstimate(context)) " (net)"
            else " (gross)"
        } else if (biometrics.userSex != null && biometrics.userAge != null && heartRate != null)
            " (net)" //cycling algorithm
        else " (${speedToMets((overview.distance!! / overview.duration!!).toFloat())} METs)"
    }

// A ride records the biometrics that were in effect when it happened; anything it
// did not capture falls back to the current settings.
fun tripBiometrics(context: Context, overview: Trip) = Biometrics(
    0,
    userAge = overview.userAge ?: getUserAge(context, overview.timestamp),
    userSex = overview.userSex ?: getUserSex(context),
    userWeight = overview.userWeight ?: getUserWeight(context),
    userHeight = overview.userHeight ?: getUserHeight(context),
    userVo2max = overview.userVo2max ?: getUserVo2max(context),
    userRestingHeartRate = overview.userRestingHeartRate ?: getUserRestingHeartRate(context),
    userMaxHeartRate = overview.userMaxHeartRate ?: getUserMaxHeartRate(context),
)

fun getCaloriesBurnedLabel(
    context: Context,
    overview: Trip,
    heartRate: Short?,
): String = getCaloriesBurnedLabel(
    context, tripBiometrics(context, overview), overview, heartRate
)

enum class CaloriesSource { POWER_METER, ESTIMATE }

data class RideCalories(val active: Int?, val total: Int?, val source: CaloriesSource)

private const val JOULES_PER_KCAL = 4184f

// The share of metabolic energy that reaches the pedals. The literature puts gross
// cycling efficiency at 20-25%; 24% is the middle of that, and the reason riders
// treat a kJ of work as roughly a kcal burned. A knob because it genuinely varies
// between riders and with intensity.
const val CYCLING_GROSS_EFFICIENCY = 0.24f

// Metabolic energy a rider spent to put this much work through the pedals. At 24%
// efficiency the conversion lands near 1 kcal per kJ, which is why the two are used
// interchangeably in cycling.
fun workToCalories(joules: Int) =
    (joules / (CYCLING_GROSS_EFFICIENCY * JOULES_PER_KCAL)).roundToInt()

// Cyclotrack estimates a single figure per ride, net or gross depending on which
// biometrics it has. Health Connect keeps the two apart, and they differ by resting
// metabolism over the ride — computable whenever height is known, since that is what
// the BMR formulas need. Without height, only the figure actually estimated is
// reported rather than inventing its counterpart.
fun rideCalories(
    context: Context,
    biometrics: Biometrics,
    overview: Trip,
    heartRate: Short?,
    powerMeasurements: Array<CyclingPowerMeasurement> = emptyArray(),
): RideCalories {
    val resting = restingCalories(biometrics, overview.duration)

    // Work at the pedals is measured rather than inferred, so it beats a formula
    // built on heart rate and VO2max whenever a power meter was on the bike. The
    // ride detail already prefers it for the same reason.
    powerMeasurements.takeIf { it.size > 1 }
        ?.let { getWork(it) }
        ?.takeIf { it > 0 }
        ?.let { joules ->
            val total = workToCalories(joules)
            return RideCalories(
                active = resting?.let { (total - it).coerceAtLeast(0) },
                total = total,
                source = CaloriesSource.POWER_METER,
            )
        }

    val estimate = getCaloriesBurned(context, biometrics, overview, heartRate)
    return when (caloriesBasis(context, biometrics, heartRate)) {
        CaloriesBasis.NET -> RideCalories(
            active = estimate,
            total = resting?.let { estimate + it },
            source = CaloriesSource.ESTIMATE,
        )

        CaloriesBasis.GROSS -> RideCalories(
            active = resting?.let { (estimate - it).coerceAtLeast(0) },
            total = estimate,
            source = CaloriesSource.ESTIMATE,
        )
    }
}

private fun restingCalories(biometrics: Biometrics, durationSeconds: Double?): Int? {
    val sex = biometrics.userSex?.name ?: return null
    val weight = biometrics.userWeight ?: return null
    val height = biometrics.userHeight ?: return null
    val age = biometrics.userAge?.roundToInt() ?: return null
    val duration = durationSeconds?.takeIf { it > 0 } ?: return null

    return (estimateBasalMetabolicRate(sex, weight, height, age)
        .toFloat() / (24 * 3600) * duration).toInt()
}

enum class CaloriesBasis {
    // Excludes resting metabolism, so it is what Health Connect calls active calories.
    NET,

    // Includes resting metabolism: Health Connect's total calories.
    GROSS,
}

// getCaloriesBurned returns net or gross depending on the settings and on which
// biometrics the ride captured, and only the label knew which. Filing a gross
// estimate as active calories would overstate the day, so the choice of record has
// to follow the same rules the label does.
fun caloriesBasis(
    context: Context,
    biometrics: Biometrics,
    heartRate: Short?,
): CaloriesBasis {
    val estimatedVo2max = biometrics.userVo2max
        ?: biometrics.userRestingHeartRate?.let { restingHeartRate ->
            biometrics.userAge?.roundToInt()?.let { age ->
                estimateVo2Max(
                    restingHeartRate,
                    biometrics.userMaxHeartRate ?: estimateMaxHeartRate(age)
                )
            }
        }
    val hasCyclingInputs =
        biometrics.userSex != null && biometrics.userAge != null && heartRate != null

    return when {
        hasCyclingInputs && estimatedVo2max != null ->
            when (biometrics.userHeight != null || !useVo2maxCalorieEstimate(context)) {
                true -> CaloriesBasis.NET
                false -> CaloriesBasis.GROSS
            }

        hasCyclingInputs -> CaloriesBasis.NET

        // The METs fallback counts resting metabolism in its 1 MET baseline.
        else -> CaloriesBasis.GROSS
    }
}

fun getCaloriesBurned(
    context: Context,
    biometrics: Biometrics,
    overview: Trip,
    heartRate: Short?,
): Int = when (useVo2maxCalorieEstimate(context)) {
    false -> estimateCaloriesBurned(
        biometrics.userSex?.name,
        biometrics.userAge?.roundToInt(),
        biometrics.userWeight!!,
        overview.duration?.toFloat()!!,
        overview.distance?.toFloat()!!,
        heartRate
    )

    else -> estimateCaloriesBurnedVo2max(
        biometrics.userSex?.name,
        biometrics.userAge?.roundToInt(),
        biometrics.userWeight!!,
        biometrics.userHeight,
        biometrics.userVo2max,
        biometrics.userRestingHeartRate,
        biometrics.userMaxHeartRate,
        overview.duration?.toFloat()!!,
        overview.distance?.toFloat()!!,
        heartRate
    )
}

fun estimateCaloriesBurned(
    sex: String?,
    age: Int?,
    weight: Float,
    duration: Float,
    distance: Float,
    heartRate: Short?,
): Int =
    if (sex != null && age != null && heartRate != null)
        estimateCyclingCaloriesBurned(sex, age, weight, duration, heartRate)
    else estimateCaloriesBurnedMets(weight, distance, duration)

fun estimateCaloriesBurnedVo2max(
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
): Int {
    val estVo2max = vo2Max ?: restingHeartRate?.let {
        estimateVo2Max(
            it,
            maximumHeartRate ?: estimateMaxHeartRate(age!!)
        )
    }
    return if (sex != null && age != null && heartRate != null && estVo2max != null) {
        if (height != null) estimateNetCaloriesBurned(
            sex,
            age,
            weight,
            height,
            estVo2max,
            duration,
            heartRate
        ) else estimateGrossCaloriesBurned(
            sex,
            age,
            weight,
            estVo2max,
            duration,
            heartRate
        )
    } else if (sex != null && age != null && heartRate != null)
        estimateCyclingCaloriesBurned(sex, age, weight, duration, heartRate)
    else estimateCaloriesBurnedMets(weight, distance, duration)
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
    return (estimateGrossCaloriesBurned(
        sex,
        age,
        weight,
        vo2Max,
        duration,
        heartRate
    ) - (estimateBasalMetabolicRate(
        sex,
        weight,
        height,
        age
    ).toFloat() / (24 * 3600) * duration)).toInt()
}

fun estimateCyclingCaloriesBurned(
    sex: String,
    age: Int,
    weight: Float,
    duration: Float,
    heartRate: Short,
): Int {
    /*  https://tourdevines.com.au/blog/how-many-calories-does-cycling-burn/
        Men: [(Age x 0.2017) — (Weight x 0.09036) + (Heart Rate x 0.6309) — 55.0969] x Time / 4.184.
        Women: [(Age x 0.074) — (Weight x 0.05741) + (Heart Rate x 0.4472) — 20.4022] x Time / 4.184.*/
    return (when (sex) {
        "MALE" -> age * 0.2017 - weight * 0.09036 + heartRate * 0.6309 - 55.0969
        else -> age * 0.074 - weight * 0.05741 + heartRate * 0.4472 - 20.4022
    } * (duration / 60) / 4.184).roundToInt()
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
    return (when (sex) {
        "MALE" -> (-95.7735 + (0.634 * heartRate) + (0.404 * vo2Max) + (0.394 * weight) + (0.271 * age))
        else -> (-59.3954 + (0.45 * heartRate) + (0.380 * vo2Max) + (0.103 * weight) + (0.274 * age))
    } / 4.184 * (duration / 60)).roundToInt()
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

suspend fun getCombinedBiometrics(
    tripId: Long,
    timestamp: Long,
    context: Context,
    coroutineScope: CoroutineScope,
    tripsRepository: TripsRepository?,
    googleFitApiService: GoogleFitApiService,
): Biometrics {
    val logTag = "getCombinedBiometrics"
    var biometrics = getBiometrics(tripId, context, timestamp)
    Log.d(logTag, "biometrics prefs: ${biometrics}")

    coroutineScope.launch {
        tripsRepository?.let {
            val tripBiometrics = it.getDefaultBiometrics(tripId)
            Log.d(logTag, "trip biometrics for ${tripId}: ${tripBiometrics}")
            biometrics = biometrics.copy(
                userSex = tripBiometrics?.userSex ?: biometrics.userSex,
                userHeight = tripBiometrics?.userHeight ?: biometrics.userHeight,
                userWeight = tripBiometrics?.userWeight ?: biometrics.userWeight,
                userAge = tripBiometrics?.userAge ?: biometrics.userAge,
                userVo2max = tripBiometrics?.userVo2max ?: biometrics.userVo2max,
                userRestingHeartRate = tripBiometrics?.userRestingHeartRate
                    ?: biometrics.userRestingHeartRate,
                userMaxHeartRate = tripBiometrics?.userMaxHeartRate
                    ?: biometrics.userMaxHeartRate,
            )
        }
        Log.d(logTag, "biometrics after trip: ${biometrics}")
        if (useGoogleFitBiometrics(context) && googleFitApiService.hasPermission()) {
            Log.d(logTag, "Getting GFit biometrics")
            val weightDeferred = async { googleFitApiService.getLatestWeight(timestamp) }
            val heightDeferred = async { googleFitApiService.getLatestHeight(timestamp) }
            val hrDeferred = async { googleFitApiService.getLatestRestingHeartRate(timestamp) }

            weightDeferred.await()?.let {
                Log.d(logTag, "biometrics google weight: ${it}")
                biometrics = biometrics.copy(userWeight = it)
            }
            heightDeferred.await()?.let {
                Log.d(logTag, "biometrics google height: ${it}")
                biometrics = biometrics.copy(userHeight = it)
            }
            hrDeferred.takeIf { useGoogleFitRestingHeartRate(context) }?.await()?.let {
                Log.d(logTag, "biometrics google resting hr: ${it}")
                biometrics = biometrics.copy(userRestingHeartRate = it)
            }
            Log.d(logTag, "biometrics google: ${biometrics}")
        }
    }.join()

    Log.d(logTag, "biometrics: ${biometrics}")
    return biometrics
}
