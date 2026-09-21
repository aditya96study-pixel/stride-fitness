package com.aditya.stride.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.data.today
import com.aditya.stride.data.type
import com.aditya.stride.ui.asClock
import com.aditya.stride.ui.asInt
import com.aditya.stride.ui.asTime
import com.aditya.stride.ui.components.ChartSeries
import com.aditya.stride.ui.components.GoalBar
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.SeriesKind
import com.aditya.stride.ui.components.StatTile
import com.aditya.stride.ui.components.ZoomableTimeChart
import com.aditya.stride.ui.dayLabel
import com.aditya.stride.ui.nav.Routes
import com.aditya.stride.ui.oneDecimal
import com.aditya.stride.ui.theme.seriesPalette
import com.aditya.stride.ui.theme.StatusCritical
import com.aditya.stride.ui.theme.StatusGood
import com.aditya.stride.ui.twoDecimals
import com.aditya.stride.ui.vm.DashboardViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(onOpen: (String) -> Unit, onOpenSettings: () -> Unit) {
    val vm: DashboardViewModel = viewModel()
    LaunchedEffect(Unit) { vm.refreshToday() }

    val profile by vm.profile.collectAsStateWithLifecycle()
    val latestWeight by vm.latestWeight.collectAsStateWithLifecycle()
    val kcalIn by vm.todayKcalIn.collectAsStateWithLifecycle()
    val waterL by vm.todayWaterL.collectAsStateWithLifecycle()
    val runs by vm.todayRuns.collectAsStateWithLifecycle()
    val exercise by vm.todayExercise.collectAsStateWithLifecycle()
    val calIn by vm.calInDaily.collectAsStateWithLifecycle()
    val calOut by vm.calOutDaily.collectAsStateWithLifecycle()
    val weightDaily by vm.weightDaily.collectAsStateWithLifecycle()
    val waterDaily by vm.waterDaily.collectAsStateWithLifecycle()
    val distanceDaily by vm.distanceDaily.collectAsStateWithLifecycle()

    val weightKg = latestWeight?.weightKg ?: profile.fallbackWeightKg
    // Resting metabolism plus ordinary daily living. Exercise you log is added on
    // top of this rather than folded into an activity multiplier, so nothing is
    // counted twice.
    val baseline = profile.bmr(weightKg) * 1.2
    val runKcal = runs.sumOf { it.kcal }
    val manualKcal = exercise.sumOf { it.kcal }
    val activityKcal = runKcal + manualKcal
    val totalOut = baseline + activityKcal
    val net = kcalIn - totalOut
    val runKmToday = runs.sumOf { it.distanceM } / 1000.0

    ScreenFrame(
        title = "Today",
        subtitle = today().dayLabel() + "  ·  " + java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM")),
        actions = { SettingsAction(onOpenSettings) },
    ) {
        // ---------- energy balance ----------
        item {
            SectionCard(title = "Energy balance") {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            (if (net >= 0) "+" else "−") + abs(net).roundToInt().toString(),
                            style = MaterialTheme.typography.displaySmall,
                            color = if (net >= 0) StatusCritical else StatusGood,
                        )
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Text(
                            "kcal",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        if (net >= 0) "above maintenance today" else "below maintenance today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            label = "Eaten",
                            value = kcalIn.toString(),
                            unit = "kcal",
                            accent = seriesPalette.calIn,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Burned",
                            value = totalOut.asInt(),
                            unit = "kcal",
                            accent = seriesPalette.calOut,
                            caption = "${baseline.roundToInt()} base + $activityKcal active",
                            modifier = Modifier.weight(1.3f),
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    GoalBar(
                        fraction = if (profile.calorieGoal > 0) {
                            kcalIn / profile.calorieGoal.toFloat()
                        } else 0f,
                        accent = seriesPalette.calIn,
                    )
                    Spacer(Modifier.height(6.dp))
                    Hint("$kcalIn of ${profile.calorieGoal} kcal daily target")
                }
            }
        }

        // ---------- quick numbers ----------
        item {
            SectionCard(title = "Where you are") {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            label = "Weight",
                            value = latestWeight?.weightKg?.oneDecimal() ?: "—",
                            unit = "kg",
                            accent = seriesPalette.weight,
                            caption = latestWeight?.epochDay?.dayLabel(),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Water",
                            value = waterL.oneDecimal(),
                            unit = "L",
                            accent = seriesPalette.water,
                            caption = "target ${profile.waterGoalL.oneDecimal()} L",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            label = "Distance",
                            value = runKmToday.twoDecimals(),
                            unit = "km",
                            accent = seriesPalette.distance,
                            caption = if (runs.isEmpty()) "no run today" else "${runs.size} run(s)",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Workouts",
                            value = exercise.size.toString(),
                            unit = if (exercise.size == 1) "entry" else "entries",
                            accent = seriesPalette.calOut,
                            caption = if (manualKcal > 0) "$manualKcal kcal logged" else null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    GoalBar(
                        fraction = if (profile.waterGoalL > 0) {
                            (waterL / profile.waterGoalL).toFloat()
                        } else 0f,
                        accent = seriesPalette.water,
                    )
                }
            }
        }

        // ---------- quick add ----------
        item {
            SectionCard(title = "Add an entry") {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickAction("Food", Icons.Rounded.Restaurant, seriesPalette.calIn, Modifier.weight(1f)) {
                            onOpen(Routes.FOOD)
                        }
                        QuickAction("Water", Icons.Rounded.LocalDrink, seriesPalette.water, Modifier.weight(1f)) {
                            onOpen(Routes.WATER)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickAction("Weight", Icons.Rounded.MonitorWeight, seriesPalette.weight, Modifier.weight(1f)) {
                            onOpen(Routes.WEIGHT)
                        }
                        QuickAction("Training", Icons.Rounded.FitnessCenter, seriesPalette.calOut, Modifier.weight(1f)) {
                            onOpen(Routes.EXERCISE)
                        }
                    }
                }
            }
        }

        // ---------- in vs out ----------
        item {
            SectionCard(
                title = "Calories in and out",
                subtitle = "Both in kcal, so they share one axis",
            ) {
                ZoomableTimeChart(
                    series = listOf(
                        ChartSeries("Eaten", seriesPalette.calIn, calIn, SeriesKind.BAR),
                        ChartSeries("Burned", seriesPalette.calOut, calOut, SeriesKind.BAR),
                    ),
                    valueLabel = { it.roundToInt().toString() },
                    unitSuffix = " kcal",
                    zeroBased = true,
                    defaultWindowDays = 14f,
                    emptyMessage = "Log a meal or a workout to see this chart",
                )
            }
        }

        item {
            SectionCard(title = "Weight", subtitle = "Every reading, newest on the right") {
                ZoomableTimeChart(
                    series = listOf(
                        ChartSeries("Weight", seriesPalette.weight, weightDaily, SeriesKind.LINE)
                    ),
                    valueLabel = { it.oneDecimal() },
                    unitSuffix = " kg",
                    defaultWindowDays = 45f,
                    emptyMessage = "No weight readings yet",
                )
            }
        }

        item {
            SectionCard(title = "Water", subtitle = "Daily total against your target") {
                ZoomableTimeChart(
                    series = listOf(
                        ChartSeries("Water", seriesPalette.water, waterDaily, SeriesKind.BAR)
                    ),
                    valueLabel = { it.oneDecimal() },
                    unitSuffix = " L",
                    zeroBased = true,
                    guide = com.aditya.stride.ui.components.ChartGuide(
                        value = profile.waterGoalL,
                        label = "target",
                        color = seriesPalette.water,
                    ),
                    defaultWindowDays = 21f,
                    emptyMessage = "No water logged yet",
                )
            }
        }

        item {
            SectionCard(
                title = "Distance",
                subtitle = "Kilometres per day, all activities together",
            ) {
                ZoomableTimeChart(
                    series = listOf(
                        ChartSeries("Distance", seriesPalette.distance, distanceDaily, SeriesKind.BAR)
                    ),
                    valueLabel = { it.twoDecimals() },
                    unitSuffix = " km",
                    zeroBased = true,
                    defaultWindowDays = 21f,
                    emptyMessage = "Nothing recorded yet",
                )
            }
        }

        if (runs.isNotEmpty() || exercise.isNotEmpty()) {
            item {
                SectionCard(title = "Today's activity") {
                    Column {
                        runs.forEach { run ->
                            com.aditya.stride.ui.components.EntryRow(
                                title = run.type.label + " · " +
                                    (run.distanceM / 1000.0).twoDecimals() + " km",
                                subtitle = run.startTime.asTime() + "  ·  " + run.movingTimeMs.asClock(),
                                trailing = "${run.kcal} kcal",
                                accent = colourFor(run.type),
                            )
                        }
                        exercise.forEach { entry ->
                            com.aditya.stride.ui.components.EntryRow(
                                title = entry.activity,
                                subtitle = entry.timestamp.asTime() +
                                    if (entry.durationMin > 0) "  ·  ${entry.durationMin} min" else "",
                                trailing = "${entry.kcal} kcal",
                                accent = seriesPalette.calOut,
                            )
                        }
                    }
                }
            }
        }

        item {
            Hint("Skip a day and the charts show the gap rather than inventing a value.")
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.16f),
            contentColor = accent,
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.height(19.dp))
        Spacer(Modifier.padding(horizontal = 3.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}
