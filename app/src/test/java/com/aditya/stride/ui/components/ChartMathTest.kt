package com.aditya.stride.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChartMathTest {

    @Test
    fun strideGrowsUntilLabelsFit() {
        // Ticks 30 px apart, labels 40 px wide with a 10 px gap: every 2nd tick.
        assertEquals(2, labelStride(tickPitchPx = 30f, widestLabelPx = 40f, gapPx = 10f))
        assertEquals(1, labelStride(tickPitchPx = 60f, widestLabelPx = 40f, gapPx = 10f))
        // 50 / 11 needs 5; rounded up to a natural step of 6.
        assertEquals(6, labelStride(tickPitchPx = 11f, widestLabelPx = 40f, gapPx = 10f))
        assertEquals(24, labelStride(tickPitchPx = 3f, widestLabelPx = 60f, gapPx = 10f))
    }

    @Test
    fun thinnedLabelsStayPutWhenPanning() {
        // Days: panning a day at a time must not change which days carry a label.
        val stride = 3L
        fun labelled(start: Float) = dateTicks(start, 10f).ticks
            .filter { Math.floorMod(it.ordinal, stride) == 0L }
            .map { it.day }
            .toSet()
        val a = labelled(20_000f)
        val b = labelled(20_001f)
        val overlap = (a intersect b)
        assertTrue(overlap.isNotEmpty())
        (20_001..20_010).map { it.toFloat() }.forEach { day ->
            assertEquals(day in a, day in b)
        }
    }

    @Test
    fun thinnedMonthsLandOnQuarters() {
        val start = LocalDate.of(2025, 1, 1).toEpochDay().toFloat()
        val labels = dateTicks(start, 700f).ticks
            .filter { Math.floorMod(it.ordinal, 3L) == 0L }
            .map { it.label }
        assertEquals(listOf("Jan", "Apr", "Jul", "Oct"), labels.take(4))
    }

    @Test
    fun ticksReportTheirClosestSpacing() {
        assertEquals(1f, dateTicks(0f, 10f).minPitchDays)
        assertEquals(7f, dateTicks(0f, 30f).minPitchDays)
        assertEquals(28f, dateTicks(0f, 400f).minPitchDays)
    }
}
