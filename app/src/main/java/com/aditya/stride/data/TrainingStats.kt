package com.aditya.stride.data

/**
 * Consistency figures, computed in Kotlin from the rows in the window rather than in SQL,
 * because three different windows over the same data is one query and three divisions.
 */

/** Days marked trained within the last [windowDays] ending on [endDay], inclusive. */
fun List<TrainingDay>.trainedDaysIn(windowDays: Int, endDay: Long): Int {
    val from = endDay - windowDays + 1
    return count { it.trained && it.epochDay in from..endDay }
}

/**
 * Sessions per week over a window.
 *
 * Deliberately divides by the whole window rather than by however many days happen to
 * have rows: a month with four workouts in it is one a week, not four a week, and a
 * missed day is a real answer here.
 */
fun List<TrainingDay>.sessionsPerWeek(windowDays: Int, endDay: Long): Double {
    if (windowDays <= 0) return 0.0
    return trainedDaysIn(windowDays, endDay) / (windowDays / 7.0)
}

/** Trained days this calendar week, Monday-based like the reminder weekday mask. */
fun List<TrainingDay>.trainedThisWeek(today: Long): Int {
    // epochDay 0 was a Thursday, so Monday-of-week is found by shifting by 3.
    val daysSinceMonday = ((today + 3) % 7 + 7) % 7
    val monday = today - daysSinceMonday
    return count { it.trained && it.epochDay in monday..today }
}

/** Average completion across the days that recorded a percentage, or null if none did. */
fun List<TrainingDay>.averagePercentPlanned(windowDays: Int, endDay: Long): Int? {
    val from = endDay - windowDays + 1
    val values = filter { it.trained && it.epochDay in from..endDay }
        .mapNotNull { it.percentPlanned }
    return if (values.isEmpty()) null else values.average().toInt()
}
