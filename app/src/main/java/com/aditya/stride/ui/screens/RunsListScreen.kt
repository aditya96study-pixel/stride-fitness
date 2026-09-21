package com.aditya.stride.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.data.SessionFilter
import com.aditya.stride.data.matching
import com.aditya.stride.data.type
import com.aditya.stride.tracking.formatPace
import com.aditya.stride.ui.asClock
import com.aditya.stride.ui.components.ActivityFilterRow
import com.aditya.stride.ui.components.EntryRow
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.ZoomableTimeChart
import com.aditya.stride.ui.components.activitySeries
import com.aditya.stride.ui.components.describe
import com.aditya.stride.ui.dayLabel
import com.aditya.stride.ui.twoDecimals
import com.aditya.stride.ui.vm.RunHistoryViewModel

@Composable
fun RunsListScreen(onBack: () -> Unit, onOpenRun: (Long) -> Unit) {
    val vm: RunHistoryViewModel = viewModel()
    val runs by vm.runs.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf(SessionFilter()) }
    val visible = runs.matching(filter)
    val totalKm = visible.sumOf { it.distanceM } / 1000.0

    ScreenFrame(
        title = "History",
        subtitle = "${visible.size} sessions  ·  ${totalKm.twoDecimals()} km",
        onBack = onBack,
    ) {
        item {
            SectionCard(title = "Show", subtitle = filter.describe()) {
                ActivityFilterRow(filter = filter, onChange = { filter = it })
            }
        }

        item {
            SectionCard(title = "Distance per day") {
                Column {
                    ZoomableTimeChart(
                        series = activitySeries(runs, filter),
                        valueLabel = { it.twoDecimals() },
                        unitSuffix = " km",
                        zeroBased = true,
                        chartHeight = 250.dp,
                        defaultWindowDays = 28f,
                        emptyMessage = if (runs.isEmpty()) {
                            "Nothing recorded yet"
                        } else {
                            "Nothing matches those filters"
                        },
                    )
                }
            }
        }

        item {
            SectionCard(title = "Sessions") {
                Column {
                    if (runs.isEmpty()) {
                        Hint(
                            "Record a run or a walk, or log a treadmill session, and it will " +
                                "appear here."
                        )
                    } else if (visible.isEmpty()) {
                        Hint("No sessions match those filters.")
                        Spacer(Modifier.height(4.dp))
                    }
                    visible.forEach { run ->
                        val pace = if (run.distanceM > 50) {
                            formatPace((run.movingTimeMs / 1000.0) / (run.distanceM / 1000.0)) +
                                " /km"
                        } else {
                            "--:--"
                        }
                        EntryRow(
                            title = run.type.label + "  ·  " +
                                (run.distanceM / 1000.0).twoDecimals() + " km  ·  $pace",
                            subtitle = run.epochDay.dayLabel() + "  ·  " +
                                run.movingTimeMs.asClock() + "  ·  ${run.kcal} kcal",
                            trailing = "",
                            accent = colourFor(run.type),
                            onClick = { onOpenRun(run.id) },
                        )
                    }
                }
            }
        }
    }
}
