package com.kvl.cyclotrack

import com.kvl.cyclotrack.util.*
import org.junit.Assert
import org.junit.Test

class BiometricUtilitiesTests {

    @Test
    fun estimateCaloriesBurnedVo2max_invalid_sex_defaults_female() {
        val duration = 40 * 60f
        val distance = 16000f
        val heartRate = 169.toShort()
        Assert.assertEquals(
            460,
            estimateCaloriesBurnedVo2max(
                "OTTER",
                20, 70f, null, null,
                60,
                null,
                duration,
                distance,
                heartRate
            )
        )
    }

    @Test
    fun estimateCaloriesBurnedVo2max_missing_resting_heart_rate() {
        val bodyMass = 70f
        val duration = 40 * 60f
        val distance = 16000f
        val heartRate = 169.toShort()
        Assert.assertEquals(
            estimateCyclingCaloriesBurned("MALE", 20, bodyMass, duration, heartRate),
            estimateCaloriesBurnedVo2max(
                "MALE",
                20, bodyMass, null, null,
                null,
                null,
                duration,
                distance,
                heartRate
            )
        )
        Assert.assertEquals(
            estimateCyclingCaloriesBurned("FEMALE", 20, bodyMass, duration, heartRate),
            estimateCaloriesBurnedVo2max(
                "FEMALE",
                20, bodyMass, null, null,
                null,
                null,
                duration,
                distance,
                heartRate
            )
        )
    }

    @Test
    fun estimateCaloriesBurnedVo2max_missing_heartRate() {
        val age = 20
        val bodyMass = 70f
        val duration = 40 * 60f
        val distance = 16000f
        Assert.assertEquals(
            estimateCaloriesBurnedMets(bodyMass, distance, duration),
            estimateCaloriesBurnedVo2max(
                "MALE",
                age, bodyMass, null, null,
                null,
                null,
                duration,
                distance,
                null
            )
        )
        Assert.assertEquals(
            estimateCaloriesBurnedMets(bodyMass, distance, duration),
            estimateCaloriesBurnedVo2max(
                "FEMALE",
                age, bodyMass, null, null,
                null,
                null,
                duration,
                distance,
                null
            )
        )
    }


    @Test
    fun estimateCaloriesBurnedVo2max_no_heart_rate_defaults() {
        val bodyMass = 86.12f
        val duration = 58.5f * 60f
        val distance = 22853f
        val heartRate = null
        Assert.assertEquals(
            estimateCaloriesBurnedMets(bodyMass, distance, duration),
            estimateCaloriesBurnedVo2max(
                "MALE",
                40, bodyMass, null, null,
                60,
                null,
                duration,
                distance,
                heartRate
            )
        )
    }

    @Test
    fun estimateCaloriesBurnedVo2max_only_required_fields() {
        val duration = 40 * 60f
        val distance = 16000f
        val heartRate = 169.toShort()
        Assert.assertEquals(
            615,
            estimateCaloriesBurnedVo2max(
                "MALE",
                20, 70f, null, null,
                60,
                null,
                duration,
                distance,
                heartRate
            )
        )
    }

    @Test
    fun estimateCaloriesBurnedVo2max_netCalories_vo2maxKnown() {
        var sex = "MALE"
        var age = 40
        var weight = 86.18f
        var height = 1.822f
        var vo2max = 51f
        val duration = 40 * 60f
        val distance = 16000f
        val heartRate = 169.toShort()

        Assert.assertEquals(
            estimateNetCaloriesBurned(
                sex,
                age,
                weight,
                height,
                vo2max,
                duration,
                heartRate
            ),
            estimateCaloriesBurnedVo2max(
                sex,
                age,
                weight,
                height,
                vo2max,
                null,
                null,
                duration,
                distance,
                heartRate
            )
        )

        sex = "FEMALE"
        age = 20
        Assert.assertEquals(
            estimateNetCaloriesBurned(
                sex,
                age,
                weight,
                height,
                vo2max,
                duration,
                heartRate
            ),
            estimateCaloriesBurnedVo2max(
                sex,
                age,
                weight,
                height,
                vo2max,
                null,
                null,
                duration,
                distance,
                heartRate
            )
        )

        weight = 60f
        vo2max = 40f
        height = 1.6f
        Assert.assertEquals(
            estimateNetCaloriesBurned(
                sex,
                age,
                weight,
                height,
                vo2max,
                duration,
                heartRate
            ),
            estimateCaloriesBurnedVo2max(
                sex,
                age,
                weight,
                height,
                vo2max,
                null,
                null,
                duration,
                distance,
                heartRate
            )
        )
    }

    @Test
    fun estimateCaloriesBurnedVo2max_netCalories_vo2maxUnknown() {
        var sex = "MALE"
        var age = 40
        var weight = 86.18f
        var height = 1.822f
        val vo2max: Float? = null
        val restingHr = 60
        var maxHr: Int? = null
        val duration = 40 * 60f
        val distance = 16000f
        val heartRate = 169.toShort()

        Assert.assertEquals(
            estimateNetCaloriesBurned(
                sex,
                age,
                weight,
                height,
                estimateVo2Max(restingHr, estimateMaxHeartRate(age)),
                duration,
                heartRate
            ),
            estimateCaloriesBurnedVo2max(
                sex,
                age,
                weight,
                height,
                vo2max,
                restingHr,
                maxHr,
                duration,
                distance,
                heartRate
            )
        )

        sex = "FEMALE"
        age = 20
        Assert.assertEquals(
            estimateNetCaloriesBurned(
                sex,
                age,
                weight,
                height,
                estimateVo2Max(restingHr, estimateMaxHeartRate(age)),
                duration,
                heartRate
            ),
            estimateCaloriesBurnedVo2max(
                sex,
                age,
                weight,
                height,
                vo2max,
                restingHr,
                maxHr,
                duration,
                distance,
                heartRate
            )
        )

        weight = 60f
        height = 1.6f
        maxHr = 190
        Assert.assertEquals(
            estimateNetCaloriesBurned(
                sex,
                age,
                weight,
                height,
                estimateVo2Max(restingHr, maxHr),
                duration,
                heartRate
            ),
            estimateCaloriesBurnedVo2max(
                sex,
                age,
                weight,
                height,
                vo2max,
                restingHr,
                maxHr,
                duration,
                distance,
                heartRate
            )
        )
    }

    @Test
    fun estimateCaloriesBurnedVo2max_grossCalories_heightUnknown() {
        var sex = "MALE"
        var age = 40
        var weight = 86.18f
        val height: Float? = null
        var vo2max: Float? = null
        val restingHr = 60
        var maxHr: Int? = null
        val duration = 40 * 60f
        val distance = 16000f
        val heartRate = 169.toShort()

        Assert.assertEquals(
            estimateGrossCaloriesBurned(
                sex,
                age,
                weight,
                estimateVo2Max(restingHr, estimateMaxHeartRate(age)),
                duration,
                heartRate
            ),
            estimateCaloriesBurnedVo2max(
                sex,
                age,
                weight,
                height,
                vo2max,
                restingHr,
                maxHr,
                duration,
                distance,
                heartRate
            )
        )

        sex = "FEMALE"
        age = 20
        Assert.assertEquals(
            estimateGrossCaloriesBurned(
                sex,
                age,
                weight,
                estimateVo2Max(restingHr, estimateMaxHeartRate(age)),
                duration,
                heartRate
            ),
            estimateCaloriesBurnedVo2max(
                sex,
                age,
                weight,
                height,
                vo2max,
                restingHr,
                maxHr,
                duration,
                distance,
                heartRate
            )
        )

        weight = 60f
        maxHr = 190
        Assert.assertEquals(
            estimateGrossCaloriesBurned(
                sex,
                age,
                weight,
                estimateVo2Max(restingHr, maxHr),
                duration,
                heartRate
            ),
            estimateCaloriesBurnedVo2max(
                sex,
                age,
                weight,
                height,
                vo2max,
                restingHr,
                maxHr,
                duration,
                distance,
                heartRate
            )
        )

        vo2max = 50f
        Assert.assertEquals(
            estimateGrossCaloriesBurned(
                sex,
                age,
                weight,
                vo2max,
                duration,
                heartRate
            ),
            estimateCaloriesBurnedVo2max(
                sex,
                age,
                weight,
                height,
                vo2max,
                restingHr,
                maxHr,
                duration,
                distance,
                heartRate
            )
        )
    }

    @Test
    fun calculateGrossCaloriesBurned_valid() {
        Assert.assertEquals(
            736,
            estimateGrossCaloriesBurned("MALE", 40, 86.18f, 51f, 40 * 60f + 8f, 169)
        )
        Assert.assertEquals(
            619,
            estimateGrossCaloriesBurned("MALE", 40, 86.18f, 51f, 40 * 60f, 150)
        )
        Assert.assertEquals(
            607,
            estimateGrossCaloriesBurned("MALE", 40, 86.18f, 48f, 40 * 60f, 150)
        )
        Assert.assertEquals(
            37,
            estimateGrossCaloriesBurned("MALE", 40, 86.18f, 51f, 40 * 60f, 54)
        )
        Assert.assertEquals(
            1329,
            estimateGrossCaloriesBurned("MALE", 40, 86.18f, 51f, 24 * 3600f, 54)
        )
        Assert.assertEquals(
            2221,
            estimateGrossCaloriesBurned("MALE", 40, 86.18f, 48f, 24 * 3600f, 60)
        )
        Assert.assertEquals(
            6367,
            estimateGrossCaloriesBurned("MALE", 40, 86.18f, 48f, 24 * 3600f, 79)
        )
        Assert.assertEquals(
            452,
            estimateGrossCaloriesBurned("FEMALE", 40, 86.18f, 51f, 40 * 60f, 150)
        )
        Assert.assertEquals(
            39,
            estimateGrossCaloriesBurned("FEMALE", 40, 86.18f, 51f, 40 * 60f, 54)
        )
        Assert.assertEquals(
            1418,
            estimateGrossCaloriesBurned("FEMALE", 40, 86.18f, 51f, 24 * 3600f, 54)
        )
    }

    @Test
    fun calculateNetCaloriesBurned_valid() {
        Assert.assertEquals(
            685,
            estimateNetCaloriesBurned("MALE", 40, 86.18f, 1.822f, 51f, 40 * 60f + 8f, 169)
        )
        Assert.assertEquals(
            568,
            estimateNetCaloriesBurned("MALE", 40, 86.18f, 1.822f, 51f, 40 * 60f, 150)
        )
        Assert.assertEquals(
            556,
            estimateNetCaloriesBurned("MALE", 40, 86.18f, 1.822f, 48f, 40 * 60f, 150)
        )
        Assert.assertEquals(
            -13,
            estimateNetCaloriesBurned("MALE", 40, 86.18f, 1.822f, 51f, 40 * 60f, 54)
        )
        Assert.assertEquals(
            -477,
            estimateNetCaloriesBurned("MALE", 40, 86.18f, 1.822f, 51f, 24 * 3600f, 54)
        )
        Assert.assertEquals(
            415,
            estimateNetCaloriesBurned("MALE", 40, 86.18f, 1.822f, 48f, 24 * 3600f, 60)
        )
        Assert.assertEquals(
            4561,
            estimateNetCaloriesBurned("MALE", 40, 86.18f, 1.822f, 48f, 24 * 3600f, 79)
        )
        Assert.assertEquals(
            406,
            estimateNetCaloriesBurned("FEMALE", 40, 86.18f, 1.822f, 51f, 40 * 60f, 150)
        )
        Assert.assertEquals(
            -6,
            estimateNetCaloriesBurned("FEMALE", 40, 86.18f, 1.822f, 51f, 40 * 60f, 54)
        )
        Assert.assertEquals(
            -222,
            estimateNetCaloriesBurned("FEMALE", 40, 86.18f, 1.822f, 51f, 24 * 3600f, 54)
        )
    }

    @Test
    fun estimateCaloriesBurnedMets_valid() {
        Assert.assertEquals(
            602,
            estimateCaloriesBurnedMets(bodyMass = 86.12f, distance = 16000f, duration = 2400f)
        )
        Assert.assertEquals(
            880,
            estimateCaloriesBurnedMets(bodyMass = 86.12f, distance = 22853f, duration = 3504f)
        )
        Assert.assertEquals(
            880,
            estimateCaloriesBurnedMets(bodyMass = 86.12f, distance = 22853f, duration = 3504f)
        )
    }

    @Test
    fun estimateCyclingCaloriesBurned_valid() {
        Assert.assertEquals(
            497,
            estimateCyclingCaloriesBurned("MALE", 40, 86.18f, 40 * 60f + 8f, 169)
        )
        Assert.assertEquals(
            381,
            estimateCyclingCaloriesBurned("MALE", 40, 86.18f, 40 * 60f, 150)
        )
        Assert.assertEquals(
            734,
            estimateCyclingCaloriesBurned("MALE", 40, 86f, 3600f, 168)
        )
        Assert.assertEquals(
            738,
            estimateCyclingCaloriesBurned("MALE", 40, 83f, 3600f, 168)
        )
        Assert.assertEquals(
            427,
            estimateCyclingCaloriesBurned("FEMALE", 40, 86.18f, 40 * 60f, 150)
        )
        Assert.assertEquals(
            342,
            estimateCyclingCaloriesBurned("FEMALE", 40, 86.18f, 40 * 60f, 130)
        )
    }

    @Test
    fun grossVsCycling() {
        Assert.assertEquals(
            1091,
            estimateGrossCaloriesBurned("MALE", 40, 83f, 54f, 3600f, 168)
        )
        Assert.assertEquals(
            1017,
            estimateNetCaloriesBurned("MALE", 40, 83f, 1.822f, 54f, 3600f, 168)
        )
        Assert.assertEquals(
            738,
            estimateCyclingCaloriesBurned("MALE", 40, 83f, 3600f, 168)
        )
    }

    @Test
    fun calculateVo2Max_valid() {
        Assert.assertEquals(80f, estimateVo2Max(40, 208), 1e0f)
        Assert.assertEquals(56f, estimateVo2Max(55, 200), 1e0f)
        Assert.assertEquals(51f, estimateVo2Max(60, 200), 1e0f)
        Assert.assertEquals(53f, estimateVo2Max(55, 190), 1e0f)
        Assert.assertEquals(48f, estimateVo2Max(60, 190), 1e0f)
        Assert.assertEquals(55f, estimateVo2Max(54, 194), 1e0f)
    }

    @Test
    fun estimateBasalMetabolicRate_valid() {
        Assert.assertEquals(1810, estimateBasalMetabolicRate("MALE", 86.18f, 1.829f, 40))
        Assert.assertEquals(1778, estimateBasalMetabolicRate("MALE", 83f, 1.829f, 40))
        Assert.assertEquals(1644, estimateBasalMetabolicRate("FEMALE", 86.18f, 1.829f, 40))
        Assert.assertEquals(1610, estimateBasalMetabolicRate("FEMALE", 90.72f, 1.702f, 40))
        Assert.assertEquals(1383, estimateBasalMetabolicRate("FEMALE", 68f, 1.702f, 40))
    }

    @Test
    fun estimateCaloriesBurned_useCyclingEquation() {
        val sex = "MALE"
        val age = 20
        val mass = 80f
        val duration = 3600f
        val distance = 30000f
        val hr = 160.toShort()

        Assert.assertEquals(
            estimateCyclingCaloriesBurned(
                sex = sex,
                age = age,
                weight = mass,
                duration = duration,
                heartRate = hr
            ),
            estimateCaloriesBurned(
                sex = sex,
                age = age,
                weight = mass,
                duration = duration,
                distance = distance,
                heartRate = hr
            )
        )
    }

    @Test
    fun estimateCaloriesBurned_useMets() {
        val sex = "MALE"
        val age = 20
        val mass = 80f
        val duration = 3600f
        val distance = 30000f
        val hr = 160.toShort()

        Assert.assertEquals(
            estimateCaloriesBurnedMets(
                bodyMass = mass,
                duration = duration,
                distance = distance
            ),
            estimateCaloriesBurned(
                null,
                age = age,
                weight = mass,
                duration = duration,
                distance = distance,
                heartRate = hr
            )
        )
        Assert.assertEquals(
            estimateCaloriesBurnedMets(
                bodyMass = mass,
                duration = duration,
                distance = distance
            ),
            estimateCaloriesBurned(
                sex = sex,
                age = null,
                weight = mass,
                duration = duration,
                distance = distance,
                heartRate = hr
            )
        )
        Assert.assertEquals(
            estimateCaloriesBurnedMets(
                bodyMass = mass,
                duration = duration,
                distance = distance
            ),
            estimateCaloriesBurned(
                sex = null,
                age = age,
                weight = mass,
                duration = duration,
                distance = distance,
                heartRate = hr
            )
        )
        Assert.assertEquals(
            estimateCaloriesBurnedMets(
                bodyMass = mass,
                duration = duration,
                distance = distance
            ),
            estimateCaloriesBurned(
                sex = sex,
                age = age,
                weight = mass,
                duration = duration,
                distance = distance,
                heartRate = null
            )
        )
    }
}