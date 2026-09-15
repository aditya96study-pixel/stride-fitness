package com.aditya.stride.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
private val shortDateFmt = DateTimeFormatter.ofPattern("EEE d MMM")
private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a")

fun Long.asTime(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(timeFmt)

fun Long.asDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateFmt)

fun Long.asDateTime(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateTimeFmt)

fun Long.dayLabel(): String {
    val date = LocalDate.ofEpochDay(this)
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(shortDateFmt)
    }
}

fun Double.oneDecimal(): String = String.format("%.1f", this)
fun Double.twoDecimals(): String = String.format("%.2f", this)
fun Double.asInt(): String = this.roundToInt().toString()

/** 12:34 or 1:02:33 */
fun Long.asClock(): String {
    val totalSeconds = this / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

fun formatHourMinute(hour: Int, minute: Int): String {
    val suffix = if (hour < 12) "AM" else "PM"
    val h12 = when {
        hour % 12 == 0 -> 12
        else -> hour % 12
    }
    return String.format("%d:%02d %s", h12, minute, suffix)
}
