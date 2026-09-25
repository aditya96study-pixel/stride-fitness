package com.aditya.stride.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class BalancePeriod(val label: String) {
    WEEK("Weekly"),
    MONTH("Monthly"),
}

/**
 * Calories eaten against calories burned over one calendar week or month.
 *
 * Only days with food logged are counted. A day with nothing logged is far more likely
 * to be a day you did not log than a day you ate nothing, and counting it would add a
 * whole day's burn with nothing against it — an invented deficit of 2,000 kcal or so.
 */
data class PeriodBalance(
    /** First day of the period (a Monday, or the 1st), as an epoch day. */
    val start: Long,
    /** Last day of the period, as an epoch day — even when that is still in the future. */
    val end: Long,
    val daysLogged: Int,
    val eatenKcal: Double,
    /** Resting metabolism, ordinary daily living and logged exercise, for the logged days. */
    val burnedKcal: Double,
) {
    /** Positive means more was eaten than burned. */
    val netKcal: Double get() = eatenKcal - burnedKcal

    val averageNetKcal: Double get() = if (daysLogged == 0) 0.0 else netKcal / daysLogged
}

object EnergyBalance {

    /**
     * BMR times this is the burn of an ordinary day before any logged exercise. The Today
     * screen uses the same figure, so the numbers agree; exercise is added on top rather
     * than folded into a bigger multiplier, so nothing is counted twice.
     */
    const val DAILY_LIVING_FACTOR = 1.2

    /** The usual rule of thumb for the energy in a kilogram of body fat. */
    const val KCAL_PER_KG = 7700.0

    fun baselineBurn(profile: Profile, weightKg: Double): Double =
        profile.bmr(weightKg) * DAILY_LIVING_FACTOR

    /**
     * The [count] most recent periods, newest first, the first being the one containing
     * [today].
     *
     * Today itself is left out: its meals are rarely all logged until it is over, and a
     * half-logged day would read as a deficit. The Today screen covers it instead.
     *
     * @param eatenByDay kcal eaten, keyed by epoch day
     * @param activeByDay kcal burned by logged exercise and recorded sessions
     * @param weightByDay body weight on the days it was measured
     */
    fun periods(
        period: BalancePeriod,
        count: Int,
        today: Long,
        profile: Profile,
        eatenByDay: Map<Long, Double>,
        activeByDay: Map<Long, Double>,
        weightByDay: Map<Long, Double>,
    ): List<PeriodBalance> {
        val weightOn = weightLookup(weightByDay, profile.fallbackWeightKg)
        var start = periodStart(period, LocalDate.ofEpochDay(today))

        return List(count) {
            val end = when (period) {
                BalancePeriod.WEEK -> start.plusDays(6)
                BalancePeriod.MONTH -> start.with(TemporalAdjusters.lastDayOfMonth())
            }
            val firstDay = start.toEpochDay()
            val lastDay = end.toEpochDay()

            var days = 0
            var eaten = 0.0
            var burned = 0.0
            for (day in firstDay..minOf(lastDay, today - 1)) {
                val kcal = eatenByDay[day] ?: continue
                if (kcal <= 0.0) continue
                days++
                eaten += kcal
                burned += baselineBurn(profile, weightOn(day)) + (activeByDay[day] ?: 0.0)
            }

            PeriodBalance(firstDay, lastDay, days, eaten, burned).also {
                start = when (period) {
                    BalancePeriod.WEEK -> start.minusWeeks(1)
                    BalancePeriod.MONTH -> start.minusMonths(1)
                }
            }
        }
    }

    fun periodStart(period: BalancePeriod, date: LocalDate): LocalDate = when (period) {
        BalancePeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        BalancePeriod.MONTH -> date.withDayOfMonth(1)
    }

    /**
     * Weight on a given day: the latest reading on or before it, else the earliest
     * reading after it (for days before you first weighed in), else the profile's
     * fallback weight.
     */
    private fun weightLookup(weightByDay: Map<Long, Double>, fallbackKg: Double): (Long) -> Double {
        val sorted = weightByDay.toSortedMap()
        return { day ->
            sorted.headMap(day + 1).let { if (it.isEmpty()) null else it[it.lastKey()] }
                ?: sorted.values.firstOrNull()
                ?: fallbackKg
        }
    }
}
