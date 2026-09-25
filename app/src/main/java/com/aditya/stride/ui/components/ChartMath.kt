package com.aditya.stride.ui.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** A single plotted value, keyed to the calendar day it belongs to. */
data class ChartPoint(val epochDay: Long, val value: Double)

enum class SeriesKind { LINE, BAR }

data class ChartSeries(
    val name: String,
    val color: androidx.compose.ui.graphics.Color,
    val points: List<ChartPoint>,
    val kind: SeriesKind = SeriesKind.LINE,
)

/**
 * A horizontal reference line, e.g. a daily goal. Drawn dashed in the series colour
 * and deliberately unlabelled: the card's subtitle says what it is.
 */
data class ChartGuide(
    val value: Double,
    val color: androidx.compose.ui.graphics.Color,
)

data class AxisTicks(val values: List<Double>, val decimals: Int)

/**
 * Round tick values a person would actually choose — 1, 2, 2.5 or 5 times a
 * power of ten — covering the data with roughly the requested number of steps.
 */
fun niceTicks(min: Double, max: Double, target: Int = 5): AxisTicks {
    if (!min.isFinite() || !max.isFinite()) return AxisTicks(listOf(0.0, 1.0), 0)
    var lo = min
    var hi = max
    if (abs(hi - lo) < 1e-9) {
        // A flat series still deserves a readable axis.
        val pad = if (abs(hi) < 1e-9) 1.0 else abs(hi) * 0.1
        lo -= pad
        hi += pad
    }
    val rawStep = (hi - lo) / target.coerceAtLeast(1)
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val normalised = rawStep / magnitude
    val stepMultiple = when {
        normalised <= 1.0 -> 1.0
        normalised <= 2.0 -> 2.0
        normalised <= 2.5 -> 2.5
        normalised <= 5.0 -> 5.0
        else -> 10.0
    }
    val step = stepMultiple * magnitude
    val start = floor(lo / step) * step
    val end = ceil(hi / step) * step

    val values = buildList {
        var v = start
        var guard = 0
        while (v <= end + step * 0.5 && guard < 64) {
            add(v)
            v += step
            guard++
        }
    }
    val decimals = when {
        step >= 10 -> 0
        step >= 1 -> 0
        step >= 0.5 -> 1
        step >= 0.1 -> 1
        else -> 2
    }
    return AxisTicks(values, decimals)
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
private val dayOnly = DateTimeFormatter.ofPattern("d")
private val monthOnly = DateTimeFormatter.ofPattern("MMM")
private val monthYear = DateTimeFormatter.ofPattern("MMM yy")
private val fullDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

/**
 * One x-axis label. [ordinal] numbers ticks of the same granularity from a fixed point
 * in time, so thinning by `ordinal % stride` keeps the same labels however the window
 * is panned, instead of labels flickering as ticks scroll in and out of view.
 */
data class DateTick(val day: Float, val label: String, val ordinal: Long)

/**
 * The candidate labels for a window, and the smallest gap in days between two of
 * them — which is what decides how many have to be skipped to stop them colliding.
 */
data class DateTicks(val ticks: List<DateTick>, val minPitchDays: Float)

/**
 * X-axis labels that stay legible at every zoom level: individual days when
 * zoomed in, then weekly, monthly and finally quarterly as you pull back.
 */
fun dateTicks(startDay: Float, spanDays: Float): DateTicks {
    val first = floor(startDay.toDouble()).toLong()
    val last = ceil((startDay + spanDays).toDouble()).toLong()
    if (last < first) return DateTicks(emptyList(), 1f)

    val days = (first..last).map { it to LocalDate.ofEpochDay(it) }
    fun monthIndex(d: LocalDate) = d.year * 12L + (d.monthValue - 1)

    return when {
        spanDays <= 12f -> DateTicks(
            days.map { (day, d) -> DateTick(day.toFloat(), d.format(dayMonth), day) },
            minPitchDays = 1f,
        )
        spanDays <= 40f -> DateTicks(
            days.filter { (_, d) -> d.dayOfWeek.value == 1 }
                .map { (day, d) -> DateTick(day.toFloat(), d.format(dayMonth), Math.floorDiv(day, 7L)) },
            minPitchDays = 7f,
        )
        // The 1st and the 15th: 13 days apart at the closest, from the 15th of February.
        spanDays <= 120f -> DateTicks(
            days.filter { (_, d) -> d.dayOfMonth == 1 || d.dayOfMonth == 15 }
                .map { (day, d) ->
                    val half = if (d.dayOfMonth == 1) 0L else 1L
                    DateTick(
                        day.toFloat(),
                        if (d.dayOfMonth == 1) d.format(monthOnly) else d.format(dayOnly),
                        monthIndex(d) * 2 + half,
                    )
                },
            minPitchDays = 13f,
        )
        spanDays <= 800f -> DateTicks(
            days.filter { (_, d) -> d.dayOfMonth == 1 }
                .map { (day, d) -> DateTick(day.toFloat(), d.format(monthOnly), monthIndex(d)) },
            minPitchDays = 28f,
        )
        else -> DateTicks(
            days.filter { (_, d) -> d.dayOfMonth == 1 && (d.monthValue - 1) % 3 == 0 }
                .map { (day, d) -> DateTick(day.toFloat(), d.format(monthYear), monthIndex(d) / 3) },
            minPitchDays = 90f,
        )
    }
}

/**
 * How many ticks to step over between labels so that the widest label, plus a gap,
 * fits between neighbours. Rounded up to a step that reads naturally — every 2nd,
 * 3rd, 4th, 6th or 12th — so thinned month labels land on quarters and years.
 */
fun labelStride(tickPitchPx: Float, widestLabelPx: Float, gapPx: Float): Int {
    if (tickPitchPx <= 0f) return 1
    val needed = ceil(((widestLabelPx + gapPx) / tickPitchPx).toDouble()).toInt().coerceAtLeast(1)
    return listOf(1, 2, 3, 4, 6, 12).firstOrNull { it >= needed }
        ?: (ceil(needed / 12.0).toInt() * 12)
}

fun Long.formatFullDate(): String = LocalDate.ofEpochDay(this).format(fullDate)

fun Double.trimDecimals(decimals: Int): String = String.format("%.${decimals}f", this)
