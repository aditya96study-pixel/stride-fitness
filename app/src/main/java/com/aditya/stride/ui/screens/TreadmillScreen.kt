package com.aditya.stride.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.data.ActivityType
import com.aditya.stride.data.epochDayToLocalDate
import com.aditya.stride.data.today
import com.aditya.stride.data.type
import com.aditya.stride.tracking.CalorieCalc
import com.aditya.stride.tracking.formatPace
import com.aditya.stride.ui.asClock
import com.aditya.stride.ui.asTime
import com.aditya.stride.ui.components.Chip
import com.aditya.stride.ui.components.DayNavigator
import com.aditya.stride.ui.components.EntryRow
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.NumberField
import com.aditya.stride.ui.components.SaveButton
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.TextField
import com.aditya.stride.ui.components.TimeRow
import com.aditya.stride.ui.oneDecimal
import com.aditya.stride.ui.theme.seriesPalette
import com.aditya.stride.ui.twoDecimals
import com.aditya.stride.ui.vm.TreadmillViewModel
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Indoor running, typed in from the console. Nothing here comes from GPS, so the belt
 * supplies distance and time and the incline supplies what altitude normally would.
 */
@Composable
fun TreadmillScreen(onBack: () -> Unit) {
    val vm: TreadmillViewModel = viewModel()
    val day by vm.selectedDay.collectAsStateWithLifecycle()
    val sessions by vm.dayEntries.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val latestWeight by vm.latestWeight.collectAsStateWithLifecycle()

    val weightKg = latestWeight?.weightKg ?: profile.fallbackWeightKg

    val now = remember { LocalTime.now() }
    var hour by remember { mutableStateOf(now.hour) }
    var minute by remember { mutableStateOf(now.minute) }
    var distanceText by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    var inclineText by remember { mutableStateOf("0") }
    var kcalText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val distanceKm = distanceText.toDoubleOrNull()
    val minutes = minutesText.toDoubleOrNull()
    val incline = inclineText.toDoubleOrNull() ?: 0.0
    val valid = distanceKm != null && distanceKm > 0.0 &&
        minutes != null && minutes > 0.0 &&
        incline in 0.0..30.0

    val estimate = if (valid) {
        CalorieCalc.treadmillKcal(
            distanceM = distanceKm!! * 1000.0,
            minutes = minutes!!,
            inclinePercent = incline,
            weightKg = weightKg,
        ).roundToInt()
    } else {
        null
    }

    val pace = if (valid) formatPace(minutes!! * 60.0 / distanceKm!!) else null

    val treadmillToday = sessions.filter { it.type == ActivityType.TREADMILL }

    ScreenFrame(
        title = "Treadmill",
        subtitle = "Enter what the console showed",
        onBack = onBack,
    ) {
        item {
            SectionCard(title = "Log a treadmill session") {
                Column {
                    DayNavigator(
                        epochDay = day,
                        onShift = { vm.shiftDay(it) },
                        canGoForward = day < today(),
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(
                            value = distanceText,
                            onValueChange = { distanceText = it },
                            label = "Distance",
                            suffix = "km",
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = minutesText,
                            onValueChange = { minutesText = it },
                            label = "Time",
                            suffix = "min",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    NumberField(
                        value = inclineText,
                        onValueChange = { inclineText = it },
                        label = "Incline",
                        suffix = "%",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("0", "1", "2", "3", "5").forEach { value ->
                            Chip(
                                label = "$value%",
                                onClick = { inclineText = value },
                                selected = inclineText == value,
                            )
                        }
                    }

                    if (pace != null) {
                        Spacer(Modifier.height(10.dp))
                        Hint("That is $pace per kilometre.")
                    }

                    Spacer(Modifier.height(12.dp))
                    NumberField(
                        value = kcalText,
                        onValueChange = { kcalText = it },
                        label = "Calories (leave blank to use the estimate)",
                        suffix = "kcal",
                        decimal = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (estimate != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Hint(
                                "Estimate $estimate kcal at ${weightKg.roundToInt()} kg. A belt " +
                                    "has no air resistance, so this reads a few percent high — " +
                                    "type your console's figure over it if you prefer.",
                                Modifier.weight(1f),
                            )
                            TextButton(onClick = { kcalText = estimate.toString() }) {
                                Text("Use", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    TimeRow(hour = hour, minute = minute, onPick = { h, m -> hour = h; minute = m })

                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = note,
                        onValueChange = { note = it },
                        label = "Note (optional)",
                        modifier = Modifier.fillMaxWidth(),
                        imeAction = ImeAction.Done,
                    )

                    Spacer(Modifier.height(14.dp))
                    SaveButton(
                        text = "Save session",
                        enabled = valid,
                        accent = seriesPalette.treadmill,
                        onClick = {
                            val startTime = day.epochDayToLocalDate()
                                .atTime(hour, minute)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                            val auto = estimate ?: 0
                            vm.add(
                                distanceKm = distanceKm!!,
                                minutes = minutes!!,
                                inclinePercent = incline,
                                kcal = kcalText.toIntOrNull() ?: auto,
                                kcalAuto = auto,
                                startTime = startTime,
                                note = note,
                            )
                            distanceText = ""
                            minutesText = ""
                            inclineText = "0"
                            kcalText = ""
                            note = ""
                        },
                    )
                    if (!valid && (distanceText.isNotBlank() || minutesText.isNotBlank())) {
                        Spacer(Modifier.height(8.dp))
                        Hint("Needs a distance, a time, and an incline between 0 and 30%.")
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Treadmill sessions on this day",
                subtitle = "Kept apart from outdoor runs and walks",
            ) {
                Column {
                    if (treadmillToday.isEmpty()) Hint("Nothing logged for this day.")
                    treadmillToday.forEach { session ->
                        EntryRow(
                            title = (session.distanceM / 1000.0).twoDecimals() + " km",
                            subtitle = session.startTime.asTime() + "  ·  " +
                                session.movingTimeMs.asClock() +
                                if (session.inclinePercent > 0.0) {
                                    "  ·  ${session.inclinePercent.oneDecimal()}% incline"
                                } else {
                                    ""
                                },
                            trailing = "${session.kcal} kcal",
                            accent = seriesPalette.treadmill,
                            onDelete = { vm.delete(session) },
                        )
                    }
                }
            }
        }
    }
}
