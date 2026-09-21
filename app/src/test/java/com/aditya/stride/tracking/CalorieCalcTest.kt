package com.aditya.stride.tracking

import com.aditya.stride.data.ActivityType
import com.aditya.stride.tracking.CalorieCalc.VoMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieCalcTest {

    /** 1.5 m/s = 90 m/min, inside the walking band, so AUTO must be the walking equation. */
    @Test
    fun auto_belowWalkCeiling_matchesTheWalkingEquation() {
        val s = 90.0
        assertEquals(0.1 * s + 3.5, CalorieCalc.vo2(1.5, 0.0, VoMode.AUTO), 1e-9)
    }

    /** 2.4 m/s = 144 m/min, past the running floor. */
    @Test
    fun auto_aboveRunFloor_matchesTheRunningEquation() {
        val s = 144.0
        assertEquals(0.2 * s + 3.5, CalorieCalc.vo2(2.4, 0.0, VoMode.AUTO), 1e-9)
    }

    @Test
    fun auto_insideTheBlend_sitsBetweenTheTwoEquations() {
        // 2.0 m/s = 120 m/min, between 100 and 134.
        val walk = CalorieCalc.vo2(2.0, 0.0, VoMode.WALK)
        val run = CalorieCalc.vo2(2.0, 0.0, VoMode.RUN)
        val auto = CalorieCalc.vo2(2.0, 0.0, VoMode.AUTO)
        assertTrue("expected $walk < $auto < $run", auto > walk && auto < run)
    }

    /** The whole point of the modes: the same pace costs different energy. */
    @Test
    fun walkAndRunDifferAtTheSamePace() {
        val walk = CalorieCalc.runKcal(5_000.0, 2_400.0, 0.0, 72.0, VoMode.WALK)
        val run = CalorieCalc.runKcal(5_000.0, 2_400.0, 0.0, 72.0, VoMode.RUN)
        assertTrue("run $run should cost more than walk $walk", run > walk)
    }

    /** Past 140 m/min the walking equation is extrapolation, so the speed it sees is capped. */
    @Test
    fun walkMode_clampsSpeedAtTheEquationCeiling() {
        assertEquals(
            CalorieCalc.vo2(140.0 / 60.0, 0.05, VoMode.WALK),
            CalorieCalc.vo2(220.0 / 60.0, 0.05, VoMode.WALK),
            1e-9,
        )
    }

    @Test
    fun gradeIsCappedSoAltitudeNoiseCannotInventCliffs() {
        assertEquals(
            CalorieCalc.vo2(2.5, 0.25, VoMode.RUN),
            CalorieCalc.vo2(2.5, 4.0, VoMode.RUN),
            1e-9,
        )
    }

    @Test
    fun vo2NeverFallsBelowRestingMetabolism() {
        assertEquals(3.5, CalorieCalc.vo2(0.0, 0.0), 1e-9)
        assertTrue(CalorieCalc.vo2(1.0, -0.25, VoMode.WALK) >= 3.5)
    }

    /** A treadmill entry must agree with the equivalent outdoor run, incline as grade. */
    @Test
    fun treadmillMatchesTheEquivalentRunWithInclineAsGrade() {
        val fromTreadmill = CalorieCalc.treadmillKcal(
            distanceM = 6_000.0,
            minutes = 35.0,
            inclinePercent = 2.0,
            weightKg = 72.0,
        )
        val fromRun = CalorieCalc.runKcal(
            distanceM = 6_000.0,
            movingSeconds = 35.0 * 60.0,
            elevationGainM = 6_000.0 * 0.02,
            weightKg = 72.0,
            mode = VoMode.RUN,
        )
        assertEquals(fromRun, fromTreadmill, 1e-6)
    }

    @Test
    fun inclineIncreasesTheTreadmillEstimate() {
        val flat = CalorieCalc.treadmillKcal(6_000.0, 35.0, 0.0, 72.0)
        val hill = CalorieCalc.treadmillKcal(6_000.0, 35.0, 5.0, 72.0)
        assertTrue("$hill should exceed $flat", hill > flat)
    }

    @Test
    fun degenerateInputsReturnZeroRatherThanNaN() {
        assertEquals(0.0, CalorieCalc.treadmillKcal(0.0, 30.0, 0.0, 72.0), 0.0)
        assertEquals(0.0, CalorieCalc.treadmillKcal(5_000.0, 0.0, 0.0, 72.0), 0.0)
        assertEquals(0.0, CalorieCalc.runKcal(5_000.0, 0.0, 0.0, 72.0), 0.0)
        assertEquals(0.0, CalorieCalc.segmentKcal(2.5, 0.0, 60.0, 0.0), 0.0)
    }

    @Test
    fun modeForMapsWalkToWalkAndBothRunsToRun() {
        assertEquals(VoMode.WALK, CalorieCalc.modeFor(ActivityType.WALK))
        assertEquals(VoMode.RUN, CalorieCalc.modeFor(ActivityType.RUN))
        assertEquals(VoMode.RUN, CalorieCalc.modeFor(ActivityType.TREADMILL))
    }

    @Test
    fun gradeHelperRejectsImplausibleVerticals() {
        assertEquals(0.0, CalorieCalc.grade(0.5, 0.2), 1e-9)
        assertEquals(0.0, CalorieCalc.grade(10.0, 12.0), 1e-9)
        assertEquals(0.05, CalorieCalc.grade(100.0, 5.0), 1e-9)
    }
}
