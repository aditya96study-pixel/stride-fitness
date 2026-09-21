package com.aditya.stride.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.tracking.formatPace
import com.aditya.stride.ui.asClock
import com.aditya.stride.ui.components.ChartSeries
import com.aditya.stride.ui.components.EntryRow
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.SeriesKind
import com.aditya.stride.ui.components.ZoomableTimeChart
import com.aditya.stride.ui.dayLabel
import com.aditya.stride.ui.theme.seriesPalette
import com.aditya.stride.ui.twoDecimals
import com.aditya.stride.ui.vm.RunHistoryViewModel

@Composable
fun RunsListScreen(onBack: () -> Unit, onOpenRun: (Long) -> Unit) {
    val vm: RunHistoryViewModel = viewModel()
    val runs by vm.runs.collectAsStateWithLifecycle()
    val distanceDaily by vm.distanceDaily.collectAsStateWithLifecycle()

    val totalKm = runs.sumOf { it.distanceM } / 1000.0

    ScreenFrame(
        title = "Runs",
        subtitle = "${runs.size} recorded  ·  ${totalKm.twoDecimals()} km total",
        onBack = onBack,
    ) {
        item {
            SectionCard(title = "Distance per day") {
                ZoomableTimeChart(
                    series = listOf(
                        ChartSeries("Distance", seriesPalette.distance, distanceDaily, SeriesKind.BAR)
                    ),
                    valueLabel = { it.twoDecimals() },
                    unitSuffix = " km",
                    zeroBased = true,
                    chartHeight = 250.dp,
                    defaultWindowDays = 28f,
                    emptyMessage = "No runs recorded yet",
                )
            }
        }

        item {
            SectionCard(title = "All runs") {
                Column {
                    if (runs.isEmpty()) {
                        Hint("Record a run and it will appear here with its route and splits.")
                    }
                    runs.forEach { run ->
                        val pace = if (run.distanceM > 50) {
                            formatPace((run.movingTimeMs / 1000.0) / (run.distanceM / 1000.0)) +
                                " /km"
                        } else "--:--"
                        EntryRow(
                            title = (run.distanceM / 1000.0).twoDecimals() + " km  ·  $pace",
                            subtitle = run.epochDay.dayLabel() + "  ·  " +
                                run.movingTimeMs.asClock() + "  ·  ${run.kcal} kcal",
                            trailing = "",
                            accent = seriesPalette.distance,
                            onClick = { onOpenRun(run.id) },
                        )
                    }
                }
            }
        }
    }
}
