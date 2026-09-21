package com.aditya.stride.tracking

import com.aditya.stride.data.ActivityType
import kotlin.math.abs

/**
 * Energy cost of walking and running, from the ACSM metabolic equations.
 *
 * Both give gross oxygen uptake in ml O2 per kg per minute; 1 litre of O2 consumed
 * releases roughly 5 kcal, which is where the conversion below comes from.
 *
 *   walking  VO2 = 0.1 * S + 1.8 * S * G + 3.5
 *   running  VO2 = 0.2 * S + 0.9 * S * G + 3.5
 *
 * S is horizontal speed in metres/minute, G is fractional grade (rise/run).
 * Walking holds up to ~100 m/min and running from ~134 m/min; in between the two
 * are blended so a jog that drifts across the boundary does not produce a step change.
 *
 * When the activity is known, [VoMode] forces the matching equation instead of
 * inferring it from speed — a brisk walk and a slow jog at the same pace are not the
 * same effort, and the app now knows which one the user said they were doing.
 */
object CalorieCalc {

    private const val WALK_MAX_M_MIN = 100.0
    private const val RUN_MIN_M_MIN = 134.0
    private const val KCAL_PER_LITRE_O2 = 5.0

    /**
     * The ACSM walking equation is validated to about 100 m/min. Forcing it on a
     * faster "walk" extrapolates, so cap the speed it sees rather than let the
     * 1.8 grade coefficient run away.
     */
    private const val WALK_EQUATION_CEILING_M_MIN = 140.0

    /** Which metabolic equation to use. AUTO keeps the speed-blended behaviour. */
    enum class VoMode { AUTO, WALK, RUN }

    /**
     * A treadmill run uses the running equation like an outdoor one; the belt removes air
     * resistance, so it reads a few percent high, which the UI says out loud rather than
     * hides. The user's own kcal override is always still available.
     */
    fun modeFor(type: ActivityType): VoMode = when (type) {
        ActivityType.WALK -> VoMode.WALK
        ActivityType.RUN, ActivityType.TREADMILL -> VoMode.RUN
    }

    /** Gross VO2 in ml/kg/min. */
    fun vo2(speedMps: Double, grade: Double, mode: VoMode = VoMode.AUTO): Double {
        val s = (speedMps * 60.0).coerceAtLeast(0.0)
        // Cap grade at +/-25% — GPS altitude noise can otherwise invent cliffs.
        val g = grade.coerceIn(-0.25, 0.25)
        if (s <= 0.0) return 3.5
        val walk = { speed: Double -> 0.1 * speed + 1.8 * speed * g + 3.5 }
        val run = { speed: Double -> 0.2 * speed + 0.9 * speed * g + 3.5 }
        return when (mode) {
            VoMode.WALK -> walk(s.coerceAtMost(WALK_EQUATION_CEILING_M_MIN))
            VoMode.RUN -> run(s)
            VoMode.AUTO -> when {
                s <= WALK_MAX_M_MIN -> walk(s)
                s >= RUN_MIN_M_MIN -> run(s)
                else -> {
                    val t = (s - WALK_MAX_M_MIN) / (RUN_MIN_M_MIN - WALK_MAX_M_MIN)
                    val walkAt = walk(s)
                    walkAt + (run(s) - walkAt) * t
                }
            }
        }.coerceAtLeast(3.5)
    }

    /** kcal burned over one segment. */
    fun segmentKcal(
        speedMps: Double,
        grade: Double,
        seconds: Double,
        weightKg: Double,
        mode: VoMode = VoMode.AUTO,
    ): Double {
        if (seconds <= 0.0 || weightKg <= 0.0) return 0.0
        val vo2PerMin = vo2(speedMps, grade, mode)
        val litresPerMin = vo2PerMin * weightKg / 1000.0
        return litresPerMin * KCAL_PER_LITRE_O2 * (seconds / 60.0)
    }

    /**
     * Whole-run estimate from totals. Used as a sanity check and for runs
     * where per-segment data is unavailable.
     */
    fun runKcal(
        distanceM: Double,
        movingSeconds: Double,
        elevationGainM: Double,
        weightKg: Double,
        mode: VoMode = VoMode.AUTO,
    ): Double {
        if (movingSeconds <= 0.0 || distanceM <= 0.0) return 0.0
        val speed = distanceM / movingSeconds
        val grade = if (distanceM > 0) (elevationGainM / distanceM) else 0.0
        return segmentKcal(speed, grade, movingSeconds, weightKg, mode)
    }

    /**
     * Treadmill estimate. There is no GPS, so the belt gives distance and time and
     * the console's incline supplies the grade the phone cannot measure.
     *
     * A treadmill also costs slightly less than the same effort outdoors — no air
     * resistance to push through — so this reads a few percent high. The figure stays
     * editable on the session, same as a GPS run's.
     */
    fun treadmillKcal(
        distanceM: Double,
        minutes: Double,
        inclinePercent: Double,
        weightKg: Double,
        mode: VoMode = VoMode.RUN,
    ): Double {
        if (minutes <= 0.0 || distanceM <= 0.0) return 0.0
        val seconds = minutes * 60.0
        return segmentKcal(distanceM / seconds, inclinePercent / 100.0, seconds, weightKg, mode)
    }

    /**
     * MET-based estimate for anything logged by hand. Pure convenience — the
     * number stays editable.
     *   kcal = MET * 3.5 * kg / 200 * minutes
     */
    fun metKcal(met: Double, minutes: Double, weightKg: Double): Double =
        met * 3.5 * weightKg / 200.0 * minutes

    /** Common activities with their MET values, for the manual-entry estimator. */
    val metPresets: List<Pair<String, Double>> = listOf(
        "Weight training — moderate" to 3.5,
        "Weight training — vigorous" to 6.0,
        "Bodyweight / calisthenics" to 4.3,
        "Circuit training" to 7.5,
        "Cycling — leisure" to 6.0,
        "Cycling — vigorous" to 10.0,
        "Swimming — moderate" to 5.8,
        "Yoga" to 2.5,
        "Stretching / mobility" to 2.3,
        "Stair climbing" to 8.8,
        "Rowing machine" to 7.0,
        "Elliptical" to 5.0,
        "Skipping rope" to 11.0,
        "Badminton" to 5.5,
        "Football" to 7.0,
        "Cricket" to 4.8,
        "Basketball" to 6.5,
        "Walking — brisk" to 4.3,
        "Hiking" to 6.0,
        "House work" to 3.0,
    )

    /** Grade between two fixes, guarding against divide-by-zero and altitude noise. */
    fun grade(horizontalM: Double, verticalM: Double): Double =
        if (horizontalM < 1.0 || abs(verticalM) > horizontalM) 0.0 else verticalM / horizontalM
}
