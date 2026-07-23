package com.kvl.cyclotrack

import org.junit.Assert
import org.junit.Test

class GradeEstimatorTest {
    // No smoothing, short window — deterministic for unit tests.
    private val cfg = GradeConfig(gravityTauSeconds = 0.0, baroWindowMeters = 10.0)

    private fun GradeState.gravity(g: FloatArray) = GradeEstimator.onGravity(this, g, config = cfg)

    // gpsAltitude is irrelevant when calibrating from baro (gravity drives the display), pass 0.
    private fun GradeState.baro(p: Float, d: Double) = GradeEstimator.onLocation(this, p, 0.0, d, config = cfg)
    private fun GradeState.gpsRef(alt: Double, d: Double) = GradeEstimator.onLocation(this, null, alt, d, config = cfg)
    private fun slope(s: GradeState) = GradeEstimator.slope(s)

    // Grade = gY/gZ (full-vector). Sign: +Y (top-of-screen forward) up => positive (uphill).
    @Test
    fun gravityGrade_isRiseOverRun() {
        val s = GradeEstimator.seed(Double.NaN).gravity(floatArrayOf(0f, 0.98f, 9.75f))  // ~10% pitch
        Assert.assertEquals(0.98 / 9.75, slope(s), 1e-6)
    }

    // No gravity AND no GPS samples => NaN (display guards isFinite and renders 0).
    @Test
    fun noData_slopeIsNaN() {
        Assert.assertTrue(slope(GradeEstimator.seed(Double.NaN)).isNaN())
    }

    // No gravity but GPS altitudes present => least-squares fallback (migrated from CalculateSlopeTest).
    @Test
    fun gpsFallback_leastSquaresWhenNoGravity() {
        var s = GradeEstimator.seed(Double.NaN)
        listOf(0.0 to 100.0, 10.0 to 102.0, 20.0 to 101.0, 30.0 to 103.0, 40.0 to 105.0, 50.0 to 104.0)
            .forEach { (d, alt) -> s = GradeEstimator.onLocation(s, null, alt, d, config = cfg) }
        Assert.assertFalse(s.gravityWarm)
        Assert.assertEquals(0.0886, slope(s), 1e-3)
    }

    // Mount tilted ~3% but road is flat (constant pressure): baro must calibrate the tilt away.
    @Test
    fun baroCalibration_removesMountTilt() {
        var s = GradeEstimator.seed(Double.NaN)
        val tilted = floatArrayOf(0f, 0.30f, 9.79f)
        var dist = 0.0
        repeat(50) { s = s.gravity(tilted); dist += 5.0; s = s.baro(1000f, dist) }
        Assert.assertEquals(0.0, slope(s), 1e-3)
    }

    // No barometer: GPS altitude is the calibration reference. Flat GPS altitude => tilt removed.
    @Test
    fun gpsAltitudeReference_removesMountTilt() {
        var s = GradeEstimator.seed(Double.NaN)
        val tilted = floatArrayOf(0f, 0.30f, 9.79f)
        var dist = 0.0
        repeat(50) { s = s.gravity(tilted); dist += 5.0; s = s.gpsRef(100.0, dist) }
        Assert.assertEquals(0.0, slope(s), 1e-3)
    }

    // Distance-weighting: within each 30 m window a fast-flat stretch (much distance, 1 sample) and a
    // slow-climb stretch (little distance, 10 samples). The gravity mean must weight by DISTANCE (→ matches
    // the reference Δh/Δd, offset ≈ 0), not sample count (which would over-weight the climb → offset ≈ +6%).
    @Test
    fun gravityMean_isDistanceWeighted() {
        val cfg30 = GradeConfig(gravityTauSeconds = 0.0, baroWindowMeters = 30.0)
        var s = GradeEstimator.seed(Double.NaN)
        val flat = floatArrayOf(0f, 0.0f, 9.79f)        // 0% grade
        val climb = floatArrayOf(0f, 0.979f, 9.79f)     // 10% grade
        var dist = 0.0; var alt = 0.0
        var lastCommitted: Double? = null
        repeat(14) {
            s = GradeEstimator.onGravity(s, flat, config = cfg30)          // fast-flat: 20 m, 1 sample
            dist += 20.0
            s = GradeEstimator.onLocation(s, null, alt, dist, config = cfg30)
            s.committedOffset?.let { lastCommitted = it }          // commit can land on this (window-closing) call
            repeat(10) { s = GradeEstimator.onGravity(s, climb, config = cfg30) }  // slow-climb: 10 m, 10 samples
            dist += 10.0; alt += 1.0                              // 10 m × 10% = 1 m gain
            s = GradeEstimator.onLocation(s, null, alt, dist, config = cfg30)
            s.committedOffset?.let { lastCommitted = it }
        }
        Assert.assertNotNull(lastCommitted)
        Assert.assertEquals(0.0, lastCommitted!!, 0.01)   // sample-count weighting would lock ≈ +0.058
    }

    // Before the per-trip lock, the seeded offset drives the display and nothing is committed yet.
    @Test
    fun seededOffset_drivesDisplayBeforeLock() {
        var s = GradeEstimator.seed(0.0306)
        var lastCommitted: Double? = null
        val tilted = floatArrayOf(0f, 0.30f, 9.79f)     // ~0.03064
        var dist = 0.0
        repeat(5) {                                     // < minWindows windows: not locked yet
            s = s.gravity(tilted); dist += 5.0; s = s.baro(1000f, dist)
            s.committedOffset?.let { lastCommitted = it }
        }
        Assert.assertNull(lastCommitted)                // not locked
        Assert.assertFalse(s.locked)
        Assert.assertEquals(0.0, slope(s), 1e-3)        // display uses the seed
    }

    // Per-trip calibration locks to THIS trip's measured tilt regardless of the seed (no deadband).
    @Test
    fun calibration_locksToActualTilt() {
        var s = GradeEstimator.seed(0.0)                // stale/zero seed
        var lastCommitted: Double? = null
        val tilted = floatArrayOf(0f, 0.30f, 9.79f)     // ~0.03064
        var dist = 0.0
        repeat(50) {
            s = s.gravity(tilted); dist += 5.0; s = s.baro(1000f, dist)
            s.committedOffset?.let { lastCommitted = it }
        }
        Assert.assertNotNull(lastCommitted)
        Assert.assertEquals(0.03064, lastCommitted!!, 1e-3)
        Assert.assertTrue(s.locked)
        Assert.assertEquals(0.0, slope(s), 1e-3)
    }

    // Switching reference source mid-ride must NOT compute a slope across the two altitude scales.
    @Test
    fun referenceSwitch_doesNotCorruptOffset() {
        var s = GradeEstimator.seed(Double.NaN)
        val tilted = floatArrayOf(0f, 0.30f, 9.79f)
        var dist = 0.0
        repeat(40) { s = s.gravity(tilted); dist += 5.0; s = s.gpsRef(50.0, dist) }   // flat GPS ≈50 m
        repeat(40) { s = s.gravity(tilted); dist += 5.0; s = s.baro(1000f, dist) }     // baro ISA ≈110 m
        // Both phases flat; the scale jump at the switch must be ignored, so offset ≈ tilt.
        Assert.assertEquals(0.0, slope(s), 1e-3)
    }

    // 6σ remount: a genuine large tilt step (≫ threshold) re-opens calibration and reconverges to it.
    @Test
    fun remount_reconvergesOnLargeShift() {
        var s = GradeEstimator.seed(Double.NaN)
        var lastCommitted: Double? = null
        var dist = 0.0
        repeat(40) {                                 // phase 1: tilt A ≈ +3%, locks here
            s = s.gravity(floatArrayOf(0f, 0.30f, 9.79f)); dist += 5.0; s = s.baro(1000f, dist)
            s.committedOffset?.let { lastCommitted = it }
        }
        Assert.assertEquals(0.0306, lastCommitted!!, 1e-3)
        repeat(60) {                                 // phase 2: remount to tilt B ≈ -2% (5% step ≫ gate)
            s = s.gravity(floatArrayOf(0f, -0.20f, 9.79f)); dist += 5.0; s = s.baro(1000f, dist)
            s.committedOffset?.let { lastCommitted = it }
        }
        Assert.assertEquals(-0.20 / 9.79, lastCommitted!!, 2e-3)  // re-locked to the new tilt
        Assert.assertEquals(0.0, slope(s), 2e-3)                  // display correct again
    }

    // A sustained sub-threshold shift (thermal drift) must NOT trip the remount flush — it is
    // followed by the slow tracker instead: the offset creeps toward the new value, lock intact.
    @Test
    fun lockedOffset_tracksSlowDrift() {
        var s = GradeEstimator.seed(Double.NaN)
        var dist = 0.0
        repeat(40) {                                 // lock at tilt A ≈ +3.06%
            s = s.gravity(floatArrayOf(0f, 0.30f, 9.79f)); dist += 5.0; s = s.baro(1000f, dist)
        }
        Assert.assertTrue(s.locked)
        val lockedAt = s.offset
        repeat(60) {                                 // +1% shift (thermal-scale) — below the remount gate
            s = s.gravity(floatArrayOf(0f, 0.40f, 9.79f)); dist += 5.0; s = s.baro(1000f, dist)
        }
        Assert.assertTrue(s.locked)                  // no flush
        // 30 gated residuals at gain 0.01 => creep (1 - 0.99^30) * 1.02% ≈ +0.27%
        Assert.assertTrue(s.offset > lockedAt + 0.002)          // tracker followed the drift...
        Assert.assertTrue(s.offset < 0.40 / 9.79)               // ...but has not jumped to it
    }

    // Moderate per-window noise (~2% grade, normal road): SE drops, commits near the tilt.
    @Test
    fun noisyRide_moderate_commitsNearTilt() {
        var s = GradeEstimator.seed(Double.NaN)
        var lastCommitted: Double? = null
        val rng = java.util.Random(42)
        var dist = 0.0
        repeat(120) {
            val noise = rng.nextGaussian() * 0.196   // window σ ≈ 2% grade
            s = s.gravity(floatArrayOf(0f, (0.30 + noise).toFloat(), 9.79f))
            dist += 10.0; s = s.baro(1000f, dist)
            s.committedOffset?.let { lastCommitted = it }
        }
        Assert.assertNotNull(lastCommitted)
        Assert.assertEquals(0.0306, lastCommitted!!, 0.006)
    }

    // Braking: the fused sensor leaks dv/dt (reads low); the k·dv/dt term must push the display back up.
    @Test
    fun correction_countersBrakingLeak() {
        var s = GradeEstimator.seed(0.0)                        // calibrated, zero offset
        s = s.gravity(floatArrayOf(0f, 0f, 9.79f))              // flat road as the sensor reports it
        s = GradeEstimator.onLocation(s, null, 0.0, 0.0, speedMps = 10.0, timeMs = 1000, config = cfg)
        s = GradeEstimator.onLocation(s, null, 0.0, 5.0, speedMps = 8.0, timeMs = 2000, config = cfg)
        // inst dv/dt = -2, EMA beta = 1/2 => dvdt = -1; corr = 0.039*(-1) + c0 => slope = +0.038724
        Assert.assertEquals(-1.0, s.dvdt, 1e-6)
        Assert.assertEquals(0.038724, GradeEstimator.correctedSlope(s, cfg), 1e-4)
    }

    // Steady climb: load-induced pitch makes gravity over-read; the force term must deflate it.
    // Fixed point of g = raw - (0.130*g + c0): raw 10% displays ~8.83%.
    @Test
    fun correction_deflatesClimbOvershoot() {
        var s = GradeEstimator.seed(0.0)
        s = s.gravity(floatArrayOf(0f, 0.979f, 9.79f))          // raw 10%
        Assert.assertEquals(0.0882, GradeEstimator.correctedSlope(s, cfg), 1e-3)
    }

    // Descent: gravity under-reads the magnitude; the descent term must steepen it (mildly).
    @Test
    fun correction_steepensDescent() {
        var s = GradeEstimator.seed(0.0)
        s = s.gravity(floatArrayOf(0f, -0.979f, 9.79f))         // raw -10%
        Assert.assertEquals(-0.1056, GradeEstimator.correctedSlope(s, cfg), 1e-3)
    }

    // Uncalibrated (offset NaN): no correction — coefficients assume a de-biased grade.
    @Test
    fun correction_inactiveWithoutOffset() {
        val s = GradeEstimator.seed(Double.NaN).gravity(floatArrayOf(0f, 0.979f, 9.79f))
        Assert.assertEquals(0.979 / 9.79, GradeEstimator.correctedSlope(s, cfg), 1e-6)
    }

    // Steep windows must not contaminate the offset: gravity carries load-pitch overshoot on climbs,
    // so residuals are only taken where |refSlope| is small. Alternate clean flat windows with steep
    // windows whose gravity over-reads; the lock must land on the true tilt anyway.
    @Test
    fun calibration_ignoresSteepWindows() {
        var s = GradeEstimator.seed(Double.NaN)
        var lastCommitted: Double? = null
        val tilt = floatArrayOf(0f, 0.30f, 9.79f)               // true tilt ~3.06%, flat road
        val steep = floatArrayOf(0f, 1.20f, 9.79f)              // 8% road + tilt + ~1.3% overshoot
        var dist = 0.0
        var alt = 0.0
        repeat(20) {
            s = s.gravity(tilt); dist += 10.0
            s = GradeEstimator.onLocation(s, null, alt, dist, config = cfg)          // flat: residual kept
            s.committedOffset?.let { lastCommitted = it }
            s = s.gravity(steep); dist += 10.0; alt += 0.8                           // 8% > 4% cap: excluded
            s = GradeEstimator.onLocation(s, null, alt, dist, config = cfg)
            s.committedOffset?.let { lastCommitted = it }
        }
        Assert.assertNotNull(lastCommitted)
        Assert.assertEquals(0.30 / 9.79, lastCommitted!!, 2e-3)  // steep overshoot did not leak in
    }

    // Severe per-window noise (~6% grade, rough trail): never confident => keep the stored offset.
    @Test
    fun noisyRide_severe_keepsStoredOffset() {
        var s = GradeEstimator.seed(0.0306)
        var lastCommitted: Double? = null
        val rng = java.util.Random(7)
        var dist = 0.0
        repeat(120) {
            val noise = rng.nextGaussian() * 0.59    // window σ ≈ 6% grade
            s = s.gravity(floatArrayOf(0f, (0.30 + noise).toFloat(), 9.79f))
            dist += 10.0; s = s.baro(1000f, dist)
            s.committedOffset?.let { lastCommitted = it }
        }
        Assert.assertNull(lastCommitted)
    }
}
