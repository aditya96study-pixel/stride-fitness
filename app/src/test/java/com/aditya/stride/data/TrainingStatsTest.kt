package com.aditya.stride.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TrainingStatsTest {

    private fun day(epochDay: Long, trained: Boolean = true, percent: Int? = null) =
        TrainingDay(epochDay = epochDay, trained = trained, percentPlanned = percent)

    private val today = 20_000L

    @Test
    fun fourWorkoutsInAMonthIsOneAWeekNotFour() {
        val days = listOf(day(today), day(today - 7), day(today - 14), day(today - 21))
        assertEquals(4 / (30 / 7.0), days.sessionsPerWeek(30, today), 1e-9)
        assertEquals(0.933, days.sessionsPerWeek(30, today), 0.001)
    }

    @Test
    fun daysOutsideTheWindowAreExcluded() {
        val days = listOf(day(today), day(today - 29), day(today - 30), day(today - 400))
        assertEquals(2, days.trainedDaysIn(30, today))
        assertEquals(3, days.trainedDaysIn(31, today))
    }

    @Test
    fun aDeliberateNoIsNotCounted() {
        val days = listOf(day(today, trained = false), day(today - 1))
        assertEquals(1, days.trainedDaysIn(30, today))
    }

    @Test
    fun anEmptyHistoryIsZeroRatherThanUndefined() {
        assertEquals(0.0, emptyList<TrainingDay>().sessionsPerWeek(365, today), 0.0)
        assertEquals(0, emptyList<TrainingDay>().trainedThisWeek(today))
        assertNull(emptyList<TrainingDay>().averagePercentPlanned(30, today))
    }

    /** epochDay 0 was a Thursday, so the Monday shift is easy to get wrong. */
    @Test
    fun theWeekStartsOnMondayForEveryDayOfTheWeek() {
        for (offset in 0..13) {
            val epochDay = today + offset
            val date = LocalDate.ofEpochDay(epochDay)
            val expectedMonday = epochDay - (date.dayOfWeek.value - 1)
            // A day marked on that Monday must be inside the week; the day before must not.
            assertEquals(
                "week starting ${DayOfWeek.MONDAY} for $date",
                1,
                listOf(day(expectedMonday), day(expectedMonday - 1))
                    .trainedThisWeek(epochDay),
            )
        }
    }

    @Test
    fun percentagesAverageOnlyOverTheDaysThatRecordedOne() {
        val days = listOf(
            day(today, percent = 100),
            day(today - 1, percent = 50),
            day(today - 2),                          // auto-marked, no percentage
            day(today - 3, trained = false, percent = null),
        )
        assertEquals(75, days.averagePercentPlanned(30, today))
    }
}
