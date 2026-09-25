package com.aditya.stride.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EnergyBalanceTest {

    // Thursday 24 September 2026. The week began on Monday the 21st.
    private val today = LocalDate.of(2026, 9, 24).toEpochDay()
    private val monday = LocalDate.of(2026, 9, 21).toEpochDay()

    // 10*80 + 6.25*175 - 5*30 + 5 = 1748.75 kcal BMR, so 2098.5 kcal on an ordinary day.
    private val profile = Profile(heightCm = 175.0, age = 30, sex = Sex.MALE, fallbackWeightKg = 80.0)
    private val baseline = 1748.75 * 1.2

    private fun week(
        eaten: Map<Long, Double>,
        active: Map<Long, Double> = emptyMap(),
        weight: Map<Long, Double> = emptyMap(),
    ) = EnergyBalance.periods(BalancePeriod.WEEK, 3, today, profile, eaten, active, weight)

    @Test
    fun netIsEatenMinusBaselineAndExercise() {
        val result = week(
            eaten = mapOf(monday to 2500.0, monday + 1 to 1800.0),
            active = mapOf(monday to 400.0),
        ).first()
        assertEquals(2, result.daysLogged)
        assertEquals(4300.0, result.eatenKcal, 1e-9)
        assertEquals(2 * baseline + 400.0, result.burnedKcal, 1e-9)
        assertEquals(4300.0 - 2 * baseline - 400.0, result.netKcal, 1e-9)
        assertEquals(result.netKcal / 2, result.averageNetKcal, 1e-9)
    }

    @Test
    fun daysWithoutFoodAndTodayAreNotCounted() {
        val result = week(
            eaten = mapOf(monday to 2000.0, today to 500.0),
            // Exercise on a day with no food logged must not create a deficit.
            active = mapOf(monday + 1 to 600.0, today to 300.0),
        ).first()
        assertEquals(1, result.daysLogged)
        assertEquals(2000.0 - baseline, result.netKcal, 1e-9)
    }

    @Test
    fun weeksRunMondayToSundayNewestFirst() {
        val result = week(eaten = mapOf(monday - 1 to 2000.0, monday - 7 to 3000.0))
        assertEquals(monday, result[0].start)
        assertEquals(monday + 6, result[0].end)
        assertEquals(0, result[0].daysLogged)
        assertEquals(monday - 7, result[1].start)
        assertEquals(2, result[1].daysLogged)
        assertEquals(5000.0, result[1].eatenKcal, 1e-9)
        assertEquals(0, result[2].daysLogged)
    }

    @Test
    fun monthsFollowTheCalendar() {
        val result = EnergyBalance.periods(
            BalancePeriod.MONTH, 2, today, profile,
            eatenByDay = mapOf(
                LocalDate.of(2026, 9, 1).toEpochDay() to 2000.0,
                LocalDate.of(2026, 8, 31).toEpochDay() to 2000.0,
                LocalDate.of(2026, 8, 1).toEpochDay() to 2000.0,
            ),
            activeByDay = emptyMap(),
            weightByDay = emptyMap(),
        )
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), result[0].start)
        assertEquals(LocalDate.of(2026, 9, 30).toEpochDay(), result[0].end)
        assertEquals(1, result[0].daysLogged)
        assertEquals(LocalDate.of(2026, 8, 1).toEpochDay(), result[1].start)
        assertEquals(2, result[1].daysLogged)
    }

    @Test
    fun theBaselineUsesTheWeightOfTheDay() {
        val eaten = mapOf(monday - 7 to 2000.0, monday to 2000.0, monday + 2 to 2000.0)
        // Weighed 90 kg on Monday; 70 kg on Wednesday.
        val weight = mapOf(monday to 90.0, monday + 2 to 70.0)
        val result = week(eaten, weight = weight)
        val at90 = (1748.75 + 100.0) * 1.2
        val at70 = (1748.75 - 100.0) * 1.2
        assertEquals(at90 + at70, result[0].burnedKcal, 1e-9)
        // Before the first reading, the first reading stands in rather than the fallback.
        assertEquals(at90, result[1].burnedKcal, 1e-9)
    }
}
