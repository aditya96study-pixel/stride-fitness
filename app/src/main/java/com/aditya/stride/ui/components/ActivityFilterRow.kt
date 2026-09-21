package com.aditya.stride.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aditya.stride.data.ActivityType
import com.aditya.stride.data.RaceBucket
import com.aditya.stride.data.RunSession
import com.aditya.stride.data.SessionFilter
import com.aditya.stride.data.dailyDistanceKm
import com.aditya.stride.data.matching
import com.aditya.stride.data.type
import com.aditya.stride.ui.theme.seriesPalette

/**
 * The visualisation filter, shared by the Trends chart and the history list so the two
 * can never interpret a selection differently.
 *
 * No chip selected means "show everything", which is why clearing the row returns the
 * screen to its default rather than emptying it.
 */
@Composable
fun ActivityFilterRow(
    filter: SessionFilter,
    onChange: (SessionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActivityType.entries.forEach { type ->
                Chip(
                    label = type.label,
                    onClick = { onChange(filter.toggleType(type)) },
                    selected = type in filter.types,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RaceBucket.entries.forEach { bucket ->
                Chip(
                    label = bucket.label,
                    onClick = { onChange(filter.toggleBucket(bucket)) },
                    selected = bucket in filter.buckets,
                )
            }
            if (!filter.isEmpty) {
                Chip(label = "Clear", onClick = { onChange(SessionFilter()) })
            }
        }
    }
}

/**
 * One series per activity, from the filtered sessions.
 *
 * Built here rather than from the per-day SQL aggregate precisely because the default is
 * three separate lines: a single summed series would merge exactly the distinction this
 * release exists to draw. The Today screen still uses the aggregate, which is what makes
 * its totals merged.
 */
@Composable
fun activitySeries(sessions: List<RunSession>, filter: SessionFilter): List<ChartSeries> {
    val palette = seriesPalette
    val visible = sessions.matching(filter)
    return ActivityType.entries.mapNotNull { type ->
        val points = visible.filter { it.type == type }.dailyDistanceKm()
        if (points.isEmpty()) {
            null
        } else {
            ChartSeries(
                name = type.label,
                color = when (type) {
                    ActivityType.RUN -> palette.run
                    ActivityType.WALK -> palette.walk
                    ActivityType.TREADMILL -> palette.treadmill
                },
                points = points.map { ChartPoint(it.epochDay, it.value) },
                kind = SeriesKind.BAR,
            )
        }
    }
}

/** "5K walks" or "Runs and walks" — what the chips currently add up to. */
fun SessionFilter.describe(): String {
    if (isEmpty) return "Every activity, each as its own series"
    val typePart = if (types.isEmpty()) "sessions" else {
        types.sortedBy { it.ordinal }.joinToString(" and ") { it.label.lowercase() + "s" }
    }
    val bucketPart = if (buckets.isEmpty()) "" else {
        " of about " + buckets.sortedBy { it.targetM }.joinToString(" or ") { it.label }
    }
    return typePart.replaceFirstChar { it.uppercase() } + bucketPart
}
