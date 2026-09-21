package com.aditya.stride.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.data.epochDayToLocalDate
import com.aditya.stride.data.today
import com.aditya.stride.tracking.CalorieCalc
import com.aditya.stride.ui.asTime
import com.aditya.stride.ui.components.ChartSeries
import com.aditya.stride.ui.components.Chip
import com.aditya.stride.ui.components.DayNavigator
import com.aditya.stride.ui.components.EntryRow
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.NumberField
import com.aditya.stride.ui.components.SaveButton
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.SeriesKind
import com.aditya.stride.ui.components.TextField
import com.aditya.stride.ui.components.TimeRow
import com.aditya.stride.ui.components.ZoomableTimeChart
import com.aditya.stride.ui.theme.seriesPalette
import com.aditya.stride.ui.vm.ExerciseViewModel
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun ExerciseScreen(onBack: () -> Unit) {
    val vm: ExerciseViewModel = viewModel()
    val day by vm.selectedDay.collectAsStateWithLifecycle()
    val entries by vm.dayEntries.collectAsStateWithLifecycle()
    val daily by vm.daily.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val latestWeight by vm.latestWeight.collectAsStateWithLifecycle()

    val weightKg = latestWeight?.weightKg ?: profile.fallbackWeightKg

    val now = remember { LocalTime.now() }
    var hour by remember { mutableStateOf(now.hour) }
    var minute by remember { mutableStateOf(now.minute) }
    var activity by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    var kcalText by remember { mutableStateOf("") }
    var pickedMet by remember { mutableStateOf<Double?>(null) }

    val kcal = kcalText.toIntOrNull()
    val durationMin = minutesText.toIntOrNull() ?: 0
    val valid = kcal != null && kcal > 0 && activity.isNotBlank()
    val dayTotal = entries.sumOf { it.kcal }

    val estimate = pickedMet?.let { met ->
        if (durationMin > 0) CalorieCalc.metKcal(met, durationMin.toDouble(), weightKg) else null
    }

    ScreenFrame(
        title = "Workouts",
        subtitle = "Calories burned outside running",
        onBack = onBack,
    ) {
        item {
            SectionCard(title = "Log a workout") {
                Column {
                    DayNavigator(
                        epochDay = day,
                        onShift = { vm.shiftDay(it) },
                        canGoForward = day < today(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "$dayTotal kcal logged on this day",
                        style = MaterialTheme.typography.titleSmall,
                        color = seriesPalette.calOut,
                    )

                    Spacer(Modifier.height(14.dp))
                    TextField(
                        value = activity,
                        onValueChange = { activity = it },
                        label = "Activity",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CalorieCalc.metPresets.forEach { (label, met) ->
                            Chip(
                                label = label,
                                selected = activity == label,
                                onClick = {
                                    activity = label
                                    pickedMet = met
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(
                            value = minutesText,
                            onValueChange = { minutesText = it },
                            label = "Duration",
                            suffix = "min",
                            decimal = false,
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = kcalText,
                            onValueChange = { kcalText = it },
                            label = "Calories",
                            suffix = "kcal",
                            decimal = false,
                            modifier = Modifier.weight(1f),
                            imeAction = ImeAction.Done,
                        )
                    }

                    if (estimate != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Hint(
                                "Roughly ${estimate.roundToInt()} kcal at ${weightKg.roundToInt()} kg",
                                Modifier.weight(1f),
                            )
                            TextButton(onClick = { kcalText = estimate.roundToInt().toString() }) {
                                Text("Use", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    TimeRow(hour = hour, minute = minute, onPick = { h, m -> hour = h; minute = m })
                    Spacer(Modifier.height(14.dp))
                    SaveButton(
                        text = "Save workout",
                        enabled = valid,
                        accent = seriesPalette.calOut,
                        onClick = {
                            val timestamp = day.epochDayToLocalDate()
                                .atTime(hour, minute)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                            vm.add(activity, kcal!!, durationMin, timestamp, null)
                            activity = ""
                            minutesText = ""
                            kcalText = ""
                            pickedMet = null
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Hint(
                        "Pick an activity chip to get a MET-based estimate, or just type " +
                            "the number if you already know it."
                    )
                }
            }
        }

        item {
            SectionCard(title = "Workouts on this day") {
                Column {
                    if (entries.isEmpty()) Hint("Nothing logged for this day.")
                    entries.forEach { entry ->
                        EntryRow(
                            title = entry.activity,
                            subtitle = entry.timestamp.asTime() +
                                if (entry.durationMin > 0) "  ·  ${entry.durationMin} min" else "",
                            trailing = "${entry.kcal} kcal",
                            accent = seriesPalette.calOut,
                            onDelete = { vm.delete(entry) },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Burned per day", subtitle = "Manual entries only") {
                ZoomableTimeChart(
                    series = listOf(ChartSeries("Burned", seriesPalette.calOut, daily, SeriesKind.BAR)),
                    valueLabel = { it.roundToInt().toString() },
                    unitSuffix = " kcal",
                    zeroBased = true,
                    chartHeight = 260.dp,
                    defaultWindowDays = 21f,
                    emptyMessage = "Log a workout to start the chart",
                )
            }
        }
    }
}
