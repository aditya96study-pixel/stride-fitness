package com.aditya.stride.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.data.SessionFilter
import com.aditya.stride.ui.components.ActivityFilterRow
import com.aditya.stride.ui.components.ChartGuide
import com.aditya.stride.ui.components.ChartSeries
import com.aditya.stride.ui.components.activitySeries
import com.aditya.stride.ui.components.describe
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.NetBalanceCard
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.SeriesKind
import com.aditya.stride.ui.components.ZoomableTimeChart
import com.aditya.stride.ui.oneDecimal
import com.aditya.stride.ui.theme.seriesPalette
import com.aditya.stride.ui.twoDecimals
import com.aditya.stride.ui.vm.DashboardViewModel
import kotlin.math.roundToInt

@Composable
fun TrendsScreen(onOpenSettings: () -> Unit) {
    val vm: DashboardViewModel = viewModel()
    LaunchedEffect(Unit) { vm.refreshToday() }

    val profile by vm.profile.collectAsStateWithLifecycle()
    val weightDaily by vm.weightDaily.collectAsStateWithLifecycle()
    val calIn by vm.calInDaily.collectAsStateWithLifecycle()
    val calOut by vm.calOutDaily.collectAsStateWithLifecycle()
    val waterDaily by vm.waterDaily.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val netBalance by vm.netBalance.collectAsStateWithLifecycle()

    // Per-screen, deliberately: the filter on Trends and the one on the history list are
    // independent, which is more useful than pretending they are the same control.
    var filter by remember { mutableStateOf(SessionFilter()) }
    val activity = activitySeries(sessions, filter)

    ScreenFrame(
        title = "Trends",
        subtitle = "Pinch any chart to zoom, hold to read a value",
        actions = { SettingsAction(onOpenSettings) },
    ) {
        item {
            SectionCard(
                title = "Weight",
                subtitle = "Dashed stretches are days with no reading",
            ) {
                ZoomableTimeChart(
                    series = listOf(ChartSeries("Weight", seriesPalette.weight, weightDaily, SeriesKind.LINE)),
                    valueLabel = { it.oneDecimal() },
                    unitSuffix = " kg",
                    chartHeight = 300.dp,
                    defaultWindowDays = 90f,
                    emptyMessage = "No weight readings yet",
                )
            }
        }

        item {
            SectionCard(
                title = "Calories in and out",
                subtitle = "Eaten against everything burned, same axis",
            ) {
                ZoomableTimeChart(
                    series = listOf(
                        ChartSeries("Eaten", seriesPalette.calIn, calIn, SeriesKind.BAR),
                        ChartSeries("Burned", seriesPalette.calOut, calOut, SeriesKind.BAR),
                    ),
                    valueLabel = { it.roundToInt().toString() },
                    unitSuffix = " kcal",
                    zeroBased = true,
                    guide = ChartGuide(profile.calorieGoal.toDouble(), seriesPalette.calIn),
                    chartHeight = 300.dp,
                    defaultWindowDays = 30f,
                    emptyMessage = "Log meals or workouts to fill this in",
                )
            }
        }

        item { NetBalanceCard(netBalance) }

        item {
            SectionCard(title = "Water", subtitle = "Litres per day") {
                ZoomableTimeChart(
                    series = listOf(ChartSeries("Water", seriesPalette.water, waterDaily, SeriesKind.BAR)),
                    valueLabel = { it.oneDecimal() },
                    unitSuffix = " L",
                    zeroBased = true,
                    guide = ChartGuide(profile.waterGoalL, seriesPalette.water),
                    chartHeight = 280.dp,
                    defaultWindowDays = 30f,
                    emptyMessage = "No water logged yet",
                )
            }
        }

        item {
            SectionCard(
                title = "Distance by activity",
                subtitle = filter.describe(),
            ) {
                Column {
                    ActivityFilterRow(filter = filter, onChange = { filter = it })
                    Spacer(Modifier.height(12.dp))
                    ZoomableTimeChart(
                        series = activity,
                        valueLabel = { it.twoDecimals() },
                        unitSuffix = " km",
                        zeroBased = true,
                        chartHeight = 300.dp,
                        defaultWindowDays = 30f,
                        emptyMessage = if (sessions.isEmpty()) {
                            "No sessions recorded yet"
                        } else {
                            "Nothing matches those filters"
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Hint(
                        "Runs, walks and treadmill sessions are separate series, never added " +
                            "together. The distance chips narrow to efforts within about 2% of " +
                            "that distance, so like efforts sit side by side."
                    )
                }
            }
        }

        item {
            Hint(
                "Every chart holds your whole history. Pinch out far enough and you will " +
                    "see months at a time; the date labels switch from days to weeks to " +
                    "months as you go."
            )
        }
    }
}
