package com.aditya.stride.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.ui.components.ChartGuide
import com.aditya.stride.ui.components.ChartSeries
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.SeriesKind
import com.aditya.stride.ui.components.ZoomableTimeChart
import com.aditya.stride.ui.oneDecimal
import com.aditya.stride.ui.theme.SeriesCalIn
import com.aditya.stride.ui.theme.SeriesCalOut
import com.aditya.stride.ui.theme.SeriesDistance
import com.aditya.stride.ui.theme.SeriesWater
import com.aditya.stride.ui.theme.SeriesWeight
import com.aditya.stride.ui.twoDecimals
import com.aditya.stride.ui.vm.DashboardViewModel
import kotlin.math.roundToInt

@Composable
fun TrendsScreen() {
    val vm: DashboardViewModel = viewModel()
    LaunchedEffect(Unit) { vm.refreshToday() }

    val profile by vm.profile.collectAsStateWithLifecycle()
    val weightDaily by vm.weightDaily.collectAsStateWithLifecycle()
    val calIn by vm.calInDaily.collectAsStateWithLifecycle()
    val calOut by vm.calOutDaily.collectAsStateWithLifecycle()
    val waterDaily by vm.waterDaily.collectAsStateWithLifecycle()
    val distanceDaily by vm.distanceDaily.collectAsStateWithLifecycle()

    ScreenFrame(
        title = "Trends",
        subtitle = "Pinch any chart to zoom, hold to read a value",
    ) {
        item {
            SectionCard(
                title = "Weight",
                subtitle = "Dashed stretches are days with no reading",
            ) {
                ZoomableTimeChart(
                    series = listOf(ChartSeries("Weight", SeriesWeight, weightDaily, SeriesKind.LINE)),
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
                        ChartSeries("Eaten", SeriesCalIn, calIn, SeriesKind.BAR),
                        ChartSeries("Burned", SeriesCalOut, calOut, SeriesKind.BAR),
                    ),
                    valueLabel = { it.roundToInt().toString() },
                    unitSuffix = " kcal",
                    zeroBased = true,
                    guide = ChartGuide(
                        profile.calorieGoal.toDouble(),
                        "intake target",
                        SeriesCalIn,
                    ),
                    chartHeight = 300.dp,
                    defaultWindowDays = 30f,
                    emptyMessage = "Log meals or workouts to fill this in",
                )
            }
        }

        item {
            SectionCard(title = "Water", subtitle = "Litres per day") {
                ZoomableTimeChart(
                    series = listOf(ChartSeries("Water", SeriesWater, waterDaily, SeriesKind.BAR)),
                    valueLabel = { it.oneDecimal() },
                    unitSuffix = " L",
                    zeroBased = true,
                    guide = ChartGuide(profile.waterGoalL, "target", SeriesWater),
                    chartHeight = 280.dp,
                    defaultWindowDays = 30f,
                    emptyMessage = "No water logged yet",
                )
            }
        }

        item {
            SectionCard(title = "Running", subtitle = "Kilometres per day") {
                ZoomableTimeChart(
                    series = listOf(
                        ChartSeries("Distance", SeriesDistance, distanceDaily, SeriesKind.BAR)
                    ),
                    valueLabel = { it.twoDecimals() },
                    unitSuffix = " km",
                    zeroBased = true,
                    chartHeight = 280.dp,
                    defaultWindowDays = 30f,
                    emptyMessage = "No runs recorded yet",
                )
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
