package com.kvl.cyclotrack

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Realtime road grade (rise/run fraction), all in one place. Stateless functional core: callers hold
 * a [GradeState] and thread it through the pure functions. No internal mutable fields, no Android deps.
 *
 * Routing is data-driven, not flag-driven: if gravity samples have arrived ([GradeState.gravityWarm]),
 * grade comes from the gravity sensor; otherwise it falls back to a GPS-altitude least-squares fit.
 * So when onboard sensors are disabled (the gravity observer is never registered) the estimator
 * automatically uses the GPS fallback — no "sensors enabled" branch needed in the caller.
 *
 * Gravity grade: mount is screen-to-sky, top-of-screen forward, so grade = gY/gZ (full vector; Z
 * normalizes common-mode bumps). The mount isn't perfectly level, so gY/gZ carries a constant offset
 * (mount tilt). We calibrate it like wheel circumference: accumulate per-distance-window residuals
 * (windowed gravity − windowed reference; reference = baro, or GPS altitude if no barometer). The
 * residual is terrain-independent — the real grade cancels — so the offset can never absorb grade.
 * Windows on steep or accelerating road are excluded: there the gravity signal carries the dynamic
 * errors below, which would otherwise leak into the offset. The residual SAMPLES are gravity-noisy
 * (σ ~2% grade) and their variance never shrinks, so (unlike circumference, whose samples are running
 * means) we gate on the standard error of the offset ESTIMATE, SE = σ/√N, which falls as we
 * accumulate. We lock the offset once it's confident (lockSigma·SE < lockTolerance ⇒ 3·SE < 1% grade),
 * then TRACK it slowly: accelerometer bias moves with temperature (measured −0.6% over one warming
 * ride), so a frozen lock goes stale — each gated flat residual nudges the offset at offsetTrackGain.
 * A genuine remount still re-opens calibration: the recent residuals shift from the offset by
 * remountSigma × the POOLED-noise SE (6σ). A short/noisy trip never reaches the lock gate, so it
 * holds the offset seeded from the prior trip. Like circumference, calibration is per-trip (state
 * reset each trip), seeded from prefs for display.
 *
 * Dynamic correction (fitted leave-one-ride-out against a centered cross-ride baro consensus over
 * 15k windows / 30 rides; see sandbox/gps-elevation-analysis/validation/experiments):
 *   corrected = raw − offset − [k·dv/dt + b·max(g·ĝ + dv/dt, 0)/g + c·min(ĝ, 0) + c0]
 * k·dv/dt is the fused sensor's longitudinal-acceleration leak (braking reads several % low without
 * it); the b term is load-induced pitch under drive force (the bike pitches nose-up relative to the
 * road when climbing under power); the c term is the descending analogue. ĝ is the estimate itself —
 * a fixed point solved by iteration (the map is a contraction, |∂corr/∂ĝ| ≤ b). The correction is
 * memoryless in the gravity signal, so display responsiveness is unchanged; dv/dt is an EMA over a
 * few seconds to match the windows the coefficients were fit at. Applied only once an offset exists
 * (seeded or locked) — the force term needs a de-biased ĝ. Residual systematic error ≤ ~0.2% grade
 * except sustained high-torque climbing, which under-corrects by ~1-2% (pedal torque is unobservable
 * without a power meter; that term is the planned tier-2 enhancement).
 *
 * ponytail: functional core; per-trip lock-once at 3σ<1%; 6σ remount; seeded display; no UI.
 */
data class GradeConfig(
    val gravityTauSeconds: Double = 0.2,   // display EMA time constant (rate-independent; 0 ⇒ no smoothing)
    val baroWindowMeters: Double = 30.0,   // reference distance window for one residual sample
    val minWindows: Int = 8,               // minimum samples before a lock is considered
    val lockSigma: Double = 3.0,           // lock when lockSigma · SE < lockTolerance ...
    val lockTolerance: Double = 0.01,      // ... i.e. offset known to 3σ within 1% grade
    val remountWindow: Int = 16,           // recent residuals tested for a remount step
    val remountSigma: Double = 6.0,        // re-open only on a remountSigma shift (~1-in-a-billion false)
    val minPooledSd: Double = 0.01,        // floor on the noise estimate so the remount gate can't hair-trigger
    val gpsFallbackWindow: Int = 20,       // GPS samples used for the least-squares fallback
    val calibrationMaxRefSlope: Double = 0.04,  // residual windows steeper than this are excluded (load pitch)
    val calibrationMaxDvdt: Double = 0.4,       // residual windows during accel/braking are excluded (leak)
    // The leak appears in the raw signal instantly, so the correction must too: a slow dv/dt EMA
    // arrives late and then overstays, flipping the displayed sign around brake/sprint transients
    // (field-tested 2026-07-03: 5 s EMA + window-fitted k read "climbing" on braked descents).
    val dvdtTauSeconds: Double = 1.0,      // dv/dt EMA — match the leak's timescale, not the fit window's
    val dvdtLeak: Double = 0.039,          // k: directly measured instantaneous leak (scripted ride 842)
    val forcePitch: Double = 0.130,        // b: load-induced pitch per unit drive force (F/mg)
    val descentPitch: Double = -0.05,      // c: descending analogue; corpus fit (-0.113) overcorrects real
                                           //    descents at display timescale — split with field evidence
    // The offset is not constant within a ride: accelerometer bias moves with temperature (measured
    // -0.6% over a warming morning ride). After lock, each gated flat residual nudges the offset with
    // this gain — slow enough that climbs/transients can't steer it (they're gated out anyway), fast
    // enough to follow thermal drift (~tau of 1/gain flat windows ≈ 3 km).
    val offsetTrackGain: Double = 0.01,
    val persistDelta: Double = 0.002,      // persist the tracked offset only when it moves this far
    val correctionIntercept: Double = 0.000276,  // c0
)

/** All grade/calibration state. Immutable: the pure functions return a new copy. */
data class GradeState(
    val gravitySlope: Double = Double.NaN,                 // displayed near-instantaneous grade source
    val offset: Double = Double.NaN,                       // committed mount offset (seeded from prefs)
    val residuals: List<Double> = emptyList(),             // rolling window of offset samples
    val lastRefAlt: Double = Double.NaN,
    val lastRefDist: Double = Double.NaN,
    val lastRefWasBaro: Boolean = false,                   // baro vs GPS altitude scales differ
    val baroSlope: Double = Double.NaN,                    // last windowed barometric Δh/Δd; grade fallback when gravity is cold
    val gravSegSum: Double = 0.0,                          // gravity since the last location (one GPS segment)
    val gravSegCount: Int = 0,
    val gravWindowWeighted: Double = 0.0,                  // Σ segMean·Δd over the ref window (distance-weighted)
    val gravWindowDist: Double = 0.0,                      // Σ Δd over the ref window
    val lastLocationDist: Double = Double.NaN,             // distance at the previous location, for Δd
    val gpsSamples: List<Pair<Double, Double>> = emptyList(),  // (distance, gpsAltitude) for fallback
    val committedOffset: Double? = null,                   // non-null only on the call that commits; persist it
    val locked: Boolean = false,                           // offset locked; thereafter tracked slowly
    val pooledSd: Double = Double.NaN,                     // residual noise σ at lock; sets the remount gate
    val persistedOffset: Double = Double.NaN,              // last value emitted for persistence
    val lastGravityTimeMs: Long = 0,                       // for the rate-independent display EMA
    val lastSpeed: Double = Double.NaN,                    // for dv/dt
    val lastSpeedTimeMs: Long = 0,
    val dvdt: Double = 0.0,                                // EMA'd longitudinal acceleration (m/s²)
) {
    /** True once at least one gravity sample has arrived → grade comes from gravity, not GPS fallback. */
    val gravityWarm: Boolean get() = !gravitySlope.isNaN()
}

object GradeEstimator {

    private const val G = 9.81

    /** Initial state seeded from the persisted mount offset (NaN ⇒ bootstrap calibration). */
    fun seed(initialOffset: Double): GradeState = GradeState(offset = initialOffset)

    /**
     * Current grade as rise/run fraction (+ uphill). The displayed value: the raw gravity grade
     * (offset-removed, no dynamic correction) — road testing found it the best estimate. When gravity
     * isn't driving the display, falls back to the baro Δh/Δd reference, then GPS-altitude least-squares.
     */
    fun slope(state: GradeState): Double {
        if (!state.gravityWarm) return fallbackSlope(state)
        return if (state.offset.isNaN()) state.gravitySlope else state.gravitySlope - state.offset
    }

    // Non-gravity fallback: barometric Δh/Δd if available (accurate but laggy), else GPS-altitude fit.
    private fun fallbackSlope(state: GradeState): Double =
        if (state.baroSlope.isFinite()) state.baroSlope else leastSquaresFitSlope(state.gpsSamples)

    /**
     * Dynamically corrected grade (dv/dt leak + load-pitch fixed-point). Kept for reference/future use;
     * NOT the displayed value — road testing favored the raw grade above. Retained so the correction
     * model isn't lost and can be re-enabled without reconstructing it.
     */
    fun correctedSlope(state: GradeState, config: GradeConfig = GradeConfig()): Double {
        if (!state.gravityWarm) return fallbackSlope(state)
        if (state.offset.isNaN()) return state.gravitySlope
        val raw = state.gravitySlope - state.offset
        // ĝ is a fixed point of ĝ = raw − correction(ĝ); the map is a contraction (|∂corr/∂ĝ| ≤ b).
        var g = raw
        repeat(3) { g = raw - correction(g, state.dvdt, config) }
        return g
    }

    // The fitted dynamic error of the gravity signal at grade ĝ and longitudinal acceleration dv/dt.
    private fun correction(gHat: Double, dvdt: Double, config: GradeConfig): Double =
        config.dvdtLeak * dvdt +
                config.forcePitch * max(G * gHat + dvdt, 0.0) / G +
                config.descentPitch * min(gHat, 0.0) +
                config.correctionIntercept

    /** Feed each gravity sample (m/s²): [x, y, z]. Call at sensor rate; timestampMs enables the
     *  rate-independent EMA (0 ⇒ assume the nominal 5 Hz delivery). */
    fun onGravity(
        state: GradeState,
        g: FloatArray,
        timestampMs: Long = 0L,
        config: GradeConfig = GradeConfig(),
    ): GradeState {
        if (g.size < 3) return state.copy(committedOffset = null)
        val gz = g[2].toDouble()
        if (gz == 0.0) return state.copy(committedOffset = null)
        val raw = g[1].toDouble() / gz
        val dt = if (timestampMs > 0 && state.lastGravityTimeMs in 1 until timestampMs)
            (timestampMs - state.lastGravityTimeMs) / 1000.0 else 0.2
        val alpha = if (config.gravityTauSeconds <= 0.0) 1.0 else dt / (config.gravityTauSeconds + dt)
        val newSlope = if (state.gravitySlope.isNaN()) raw
        else alpha * raw + (1 - alpha) * state.gravitySlope
        return state.copy(
            gravitySlope = newSlope,
            gravSegSum = state.gravSegSum + raw,   // accumulate per GPS segment; distance-weighted at onLocation
            gravSegCount = state.gravSegCount + 1,
            lastGravityTimeMs = if (timestampMs > 0) timestampMs else state.lastGravityTimeMs,
            committedOffset = null,
        )
    }

    /**
     * Feed each location update: pressure (hPa, null if no barometer), GPS altitude (m), trip distance (m),
     * speed (m/s) and fix time (ms) for the dv/dt term of the dynamic correction.
     * Appends to the GPS fallback window and runs the gravity-offset calibration (baro reference if
     * present, else GPS altitude). Calibration no-ops when no gravity has been accumulated.
     */
    fun onLocation(
        state: GradeState,
        pressureHpa: Float?,
        gpsAltitude: Double,
        distanceMeters: Double,
        speedMps: Double = Double.NaN,
        timeMs: Long = 0L,
        config: GradeConfig = GradeConfig(),
    ): GradeState {
        val gps = (state.gpsSamples + (distanceMeters to gpsAltitude)).let {
            if (it.size > config.gpsFallbackWindow) it.takeLast(config.gpsFallbackWindow) else it
        }
        val refAltitude = if (pressureHpa != null) pressureToAltitude(pressureHpa) else gpsAltitude
        val tracked = trackDvdt(state, speedMps, timeMs, config)
        val folded = foldGravitySegment(tracked, distanceMeters)   // distance-weight gravity to match Δh/Δd
        return calibrate(
            folded.copy(gpsSamples = gps, committedOffset = null),
            refAltitude, distanceMeters, refIsBaro = pressureHpa != null, config = config,
        )
    }

    private fun pressureToAltitude(hPa: Float): Double =
        44330.0 * (1.0 - (hPa / 1013.25).toDouble().pow(0.190284))

    // EMA of longitudinal acceleration from successive speed fixes; feeds the leak/force correction.
    private fun trackDvdt(state: GradeState, speedMps: Double, timeMs: Long, config: GradeConfig): GradeState {
        if (speedMps.isNaN() || timeMs <= 0) return state
        if (state.lastSpeed.isNaN() || timeMs <= state.lastSpeedTimeMs)
            return state.copy(lastSpeed = speedMps, lastSpeedTimeMs = timeMs)
        val dt = (timeMs - state.lastSpeedTimeMs) / 1000.0
        if (dt > 10.0) return state.copy(lastSpeed = speedMps, lastSpeedTimeMs = timeMs, dvdt = 0.0)
        val inst = (speedMps - state.lastSpeed) / dt
        val beta = dt / (config.dvdtTauSeconds + dt)
        return state.copy(
            lastSpeed = speedMps, lastSpeedTimeMs = timeMs,
            dvdt = beta * inst + (1 - beta) * state.dvdt,
        )
    }

    // Fold the gravity accumulated since the last location into the window, weighted by the distance it
    // covered. Within a ~1 s GPS segment speed is ≈ constant, so this makes the window-mean distance-based
    // (matching Δh/Δd) instead of sample/time-based. Standstill segments (Δd ≤ 0) contribute nothing.
    private fun foldGravitySegment(state: GradeState, distanceMeters: Double): GradeState {
        if (state.lastLocationDist.isNaN()) return state.copy(lastLocationDist = distanceMeters)
        val segDist = distanceMeters - state.lastLocationDist
        if (state.gravSegCount <= 0 || segDist <= 0.0)
            return state.copy(gravSegSum = 0.0, gravSegCount = 0, lastLocationDist = distanceMeters)
        val segMean = state.gravSegSum / state.gravSegCount
        return state.copy(
            gravWindowWeighted = state.gravWindowWeighted + segMean * segDist,
            gravWindowDist = state.gravWindowDist + segDist,
            gravSegSum = 0.0, gravSegCount = 0,
            lastLocationDist = distanceMeters,
        )
    }

    // Accumulate window residuals; lock the offset mean once SE is small, then watch for a 6σ remount.
    private fun calibrate(
        state: GradeState,
        refAltitude: Double,
        distanceMeters: Double,
        refIsBaro: Boolean,
        config: GradeConfig,
    ): GradeState {
        // (Re)seed on the first sample, or when the reference source switches — baro ISA altitude and
        // GPS altitude sit on different absolute scales, so a slope across the two would be garbage.
        if (state.lastRefDist.isNaN() || refIsBaro != state.lastRefWasBaro) {
            return state.copy(lastRefAlt = refAltitude, lastRefDist = distanceMeters, lastRefWasBaro = refIsBaro)
        }
        if (distanceMeters - state.lastRefDist < config.baroWindowMeters) return state

        val refSlope = (refAltitude - state.lastRefAlt) / (distanceMeters - state.lastRefDist)
        // baroSlope is the barometric fallback grade — only trust it when the reference IS baro
        // (GPS-altitude Δh/Δd is too biased/noisy to display; that path uses the least-squares fallback).
        val advanced = state.copy(
            lastRefAlt = refAltitude, lastRefDist = distanceMeters,
            baroSlope = if (refIsBaro) refSlope else state.baroSlope,
        )
        if (advanced.gravWindowDist <= 0.0) return advanced    // no gravity-distance this window: advance ref only
        // Steep or accelerating windows carry the dynamic gravity errors (load pitch, dv/dt leak);
        // letting them into the residuals would bias the offset toward this ride's effort profile.
        if (abs(refSlope) > config.calibrationMaxRefSlope || abs(advanced.dvdt) > config.calibrationMaxDvdt)
            return advanced.copy(gravWindowWeighted = 0.0, gravWindowDist = 0.0)
        val residual = advanced.gravWindowWeighted / advanced.gravWindowDist - refSlope   // ≈ mount offset
        val cleared = advanced.copy(gravWindowWeighted = 0.0, gravWindowDist = 0.0)

        if (state.locked) {
            // Re-open only on a genuine remount. Threshold = remountSigma × the POOLED noise SE
            // (σ measured before lock), NOT the recent window's own variance — that collapses on a
            // coherently-biased stretch (a climb) and is what made the old detector false-trigger.
            val recent = (cleared.residuals + residual).takeLast(config.remountWindow)
            val held = cleared.copy(residuals = recent)
            if (recent.size >= config.remountWindow) {
                val sd = max(cleared.pooledSd, config.minPooledSd)
                val threshold = config.remountSigma * sd / sqrt(config.remountWindow.toDouble())
                if (abs(recent.average() - cleared.offset) > threshold)
                    return held.copy(locked = false, residuals = emptyList())   // remount: re-lock from scratch
            }
            // No remount: track slowly. The offset moves with temperature (accelerometer bias), so a
            // frozen lock goes stale over a ride; the gates above ensure only clean flat windows steer.
            val tracked = cleared.offset + config.offsetTrackGain * (residual - cleared.offset)
            val emit = abs(tracked - held.persistedOffset.let { if (it.isNaN()) cleared.offset else it }) >
                    config.persistDelta
            return held.copy(
                offset = tracked,
                persistedOffset = if (emit) tracked else held.persistedOffset,
                committedOffset = if (emit) tracked else null,
            )
        }

        // Not locked: accumulate every residual (no cap) so SE = σ/√N keeps falling with distance.
        val residuals = cleared.residuals + residual
        val windowed = cleared.copy(residuals = residuals)
        if (residuals.size < config.minWindows) return windowed

        // Lock once the offset estimate is confident: lockSigma · SE < lockTolerance (3·SE < 1% grade).
        // SE is the standard error of the mean (σ/√N); the per-sample variance never shrinks, the mean's does.
        val se = sqrt(residuals.sampleVariance() / residuals.size)
        return if (config.lockSigma * se < config.lockTolerance) {
            windowed.copy(
                offset = residuals.average(),
                pooledSd = sqrt(residuals.sampleVariance()),    // stable noise for the remount gate
                locked = true,
                committedOffset = residuals.average(),          // drive display + persist
                persistedOffset = residuals.average(),
            )
        } else windowed
    }
}
