package com.aditya.stride.data

import kotlin.math.abs

/**
 * Near-exact race distances, for looking at like-for-like efforts.
 *
 * The tolerance is `max(50 m, 2% of target)`. Consumer-phone GPS distance runs 1–2%
 * off, so ±2% is the honest band at 5K and 10K; at 1K, 2% would be 20 m, below the
 * noise floor, hence the fixed 50 m floor. The resulting windows — 950–1050,
 * 1950–2050, 2940–3060, 4900–5100, 9800–10200 — are disjoint, so a distance falls
 * into at most one bucket.
 */
enum class RaceBucket(val label: String, val targetM: Double) {
    K1("1K", 1_000.0),
    K2("2K", 2_000.0),
    K3("3K", 3_000.0),
    K5("5K", 5_000.0),
    K10("10K", 10_000.0);

    val toleranceM: Double get() = maxOf(50.0, targetM * 0.02)

    fun contains(distanceM: Double): Boolean = abs(distanceM - targetM) <= toleranceM
}

fun bucketOf(distanceM: Double): RaceBucket? = RaceBucket.entries.firstOrNull {
    it.contains(distanceM)
}

/**
 * A purely visual narrowing of the history. Empty means "show everything", which is
 * what makes clearing the chips return the screen to its default state.
 *
 * The two dimensions are orthogonal: OR within a dimension, AND across them. Picking
 * Walk and 5K means "walks of about five kilometres".
 */
data class SessionFilter(
    val types: Set<ActivityType> = emptySet(),
    val buckets: Set<RaceBucket> = emptySet(),
) {
    val isEmpty: Boolean get() = types.isEmpty() && buckets.isEmpty()

    fun matches(session: RunSession): Boolean {
        if (types.isNotEmpty() && session.type !in types) return false
        if (buckets.isNotEmpty() && buckets.none { it.contains(session.distanceM) }) return false
        return true
    }

    fun toggleType(type: ActivityType): SessionFilter =
        copy(types = if (type in types) types - type else types + type)

    fun toggleBucket(bucket: RaceBucket): SessionFilter =
        copy(buckets = if (bucket in buckets) buckets - bucket else buckets + bucket)
}

/**
 * The history list and the Trends chart both derive from this one helper, so they can
 * never disagree about what a filter means.
 */
fun List<RunSession>.matching(filter: SessionFilter): List<RunSession> =
    if (filter.isEmpty) this else filter { filter.matches(it) }

/** Kilometres per day, summed. */
fun List<RunSession>.dailyDistanceKm(): List<DailyValue> = groupToDaily { it.distanceM / 1000.0 }

/** Counted calories per day, summed. */
fun List<RunSession>.dailyKcal(): List<DailyValue> = groupToDaily { it.kcal.toDouble() }

private inline fun List<RunSession>.groupToDaily(value: (RunSession) -> Double): List<DailyValue> {
    val totals = HashMap<Long, Double>()
    for (session in this) {
        totals[session.epochDay] = (totals[session.epochDay] ?: 0.0) + value(session)
    }
    return totals.entries.sortedBy { it.key }.map { DailyValue(it.key, it.value) }
}
