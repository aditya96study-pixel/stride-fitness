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
import com.aditya.stride.data.averagePercentPlanned
import com.aditya.stride.data.epochDayToLocalDate
import com.aditya.stride.data.sessionsPerWeek
import com.aditya.stride.data.today
import com.aditya.stride.data.trainedThisWeek
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
import com.aditya.stride.ui.components.StatTile
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
    val trainingDay by vm.trainingDay.collectAsStateWithLifecycle()
    val trainingYear by vm.trainingYear.collectAsStateWithLifecycle()

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

    val endDay = today()

    ScreenFrame(
        title = "Training",
        subtitle = "Whether you trained, and what it cost you",
        onBack = onBack,
    ) {
        item {
            SectionCard(
                title = "Did you train?",
                subtitle = "One answer per day — back-fill with the arrows below",
            ) {
                Column {
                    DayNavigator(
                        epochDay = day,
                        onShift = { vm.shiftDay(it) },
                        canGoForward = day < today(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(
                            label = "Yes",
                            onClick = { vm.setTrained(true, trainingDay?.percentPlanned) },
                            selected = trainingDay?.trained == true,
                        )
                        Chip(
                            label = "No",
                            onClick = { vm.setTrained(false, null) },
                            selected = trainingDay?.trained == false,
                        )
                        if (trainingDay != null) {
                            Chip(label = "Clear", onClick = { vm.clearTrainingAnswer() })
                        }
                    }

                    if (trainingDay?.trained == true) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "How much of the planned session did you finish?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(25, 50, 75, 100).forEach { percent ->
                                Chip(
                                    label = "$percent%",
                                    onClick = { vm.setTrained(true, percent) },
                                    selected = trainingDay?.percentPlanned == percent,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // Chips cover the answers people actually give; the field is for
                        // the odd 40% without making every entry a slider drag.
                        NumberField(
                            value = trainingDay?.percentPlanned?.toString().orEmpty(),
                            onValueChange = { text ->
                                text.toIntOrNull()?.takeIf { it in 0..100 }?.let {
                                    vm.setTrained(true, it)
                                }
                            },
                            label = "Or type a percentage",
                            suffix = "%",
                            decimal = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (trainingDay?.percentPlanned == null) {
                            Spacer(Modifier.height(8.dp))
                            Hint(
                                "Marked from a logged workout. Tap a percentage to say how " +
                                    "much of it you finished."
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Consistency",
                subtitle = "Trained days per week, as a moving average",
            ) {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            "This week",
                            trainingYear.trainedThisWeek(endDay).toString(),
                            "days",
                            seriesPalette.calOut,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "1 month",
                            trainingYear.sessionsPerWeek(30, endDay).oneDecimal(),
                            "/week",
                            seriesPalette.calOut,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            "6 months",
                            trainingYear.sessionsPerWeek(182, endDay).oneDecimal(),
                            "/week",
                            seriesPalette.calOut,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "1 year",
                            trainingYear.sessionsPerWeek(365, endDay).oneDecimal(),
                            "/week",
                            seriesPalette.calOut,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    trainingYear.averagePercentPlanned(30, endDay)?.let { average ->
                        Spacer(Modifier.height(12.dp))
                        Hint(
                            "Over the last month you finished $average% of the sessions you " +
                                "planned, on the days you recorded a figure."
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Hint(
                        "Each figure divides trained days by the whole window, so four " +
                            "workouts in a month reads as one a week. Missed days count as " +
                            "missed, which is the point."
                    )
                }
            }
        }

        item {
            SectionCard(
                title = "Log a workout",
                subtitle = "Calories burned outside running",
            ) {
                Column {
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
                        "Pick an activity chip for a MET-based estimate, or type the number " +
                            "if you already know it. Saving a workout also marks the day " +
                            "trained, unless you have already answered for that day."
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
