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

/** A horizontal reference line, e.g. a daily goal. */
data class ChartGuide(
    val value: Double,
    val label: String,
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
 * X-axis labels that stay legible at every zoom level: individual days when
 * zoomed in, then weekly, monthly and finally quarterly as you pull back.
 */
fun dateTicks(startDay: Float, spanDays: Float): List<Pair<Float, String>> {
    val first = floor(startDay.toDouble()).toLong()
    val last = ceil((startDay + spanDays).toDouble()).toLong()
    if (last < first) return emptyList()

    return when {
        spanDays <= 12f -> (first..last).map {
            it.toFloat() to LocalDate.ofEpochDay(it).format(dayMonth)
        }
        spanDays <= 40f -> (first..last).filter {
            LocalDate.ofEpochDay(it).dayOfWeek.value == 1
        }.map { it.toFloat() to LocalDate.ofEpochDay(it).format(dayMonth) }
        spanDays <= 120f -> (first..last).filter {
            LocalDate.ofEpochDay(it).dayOfMonth == 1 || LocalDate.ofEpochDay(it).dayOfMonth == 15
        }.map {
            val d = LocalDate.ofEpochDay(it)
            it.toFloat() to if (d.dayOfMonth == 1) d.format(monthOnly) else d.format(dayOnly)
        }
        spanDays <= 800f -> (first..last).filter {
            LocalDate.ofEpochDay(it).dayOfMonth == 1
        }.map { it.toFloat() to LocalDate.ofEpochDay(it).format(monthOnly) }
        else -> (first..last).filter {
            val d = LocalDate.ofEpochDay(it)
            d.dayOfMonth == 1 && (d.monthValue - 1) % 3 == 0
        }.map { it.toFloat() to LocalDate.ofEpochDay(it).format(monthYear) }
    }
}

fun Long.formatFullDate(): String = LocalDate.ofEpochDay(this).format(fullDate)

fun Double.trimDecimals(decimals: Int): String = String.format("%.${decimals}f", this)
