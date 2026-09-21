package com.aditya.stride.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.aditya.stride.ui.asTime
import com.aditya.stride.ui.components.ChartSeries
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
import com.aditya.stride.ui.dayLabel
import com.aditya.stride.ui.oneDecimal
import com.aditya.stride.ui.theme.seriesPalette
import com.aditya.stride.ui.vm.WeightViewModel
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

@Composable
fun WeightScreen(onBack: () -> Unit) {
    val vm: WeightViewModel = viewModel()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val daily by vm.daily.collectAsStateWithLifecycle()

    var day by remember { mutableStateOf(today()) }
    val now = remember { LocalTime.now() }
    var hour by remember { mutableStateOf(now.hour) }
    var minute by remember { mutableStateOf(now.minute) }
    var weightText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val parsed = weightText.toDoubleOrNull()
    val valid = parsed != null && parsed > 15 && parsed < 400

    ScreenFrame(
        title = "Weight",
        subtitle = "In kilograms",
        onBack = onBack,
    ) {
        item {
            SectionCard(title = "Add a reading") {
                Column {
                    DayNavigator(
                        epochDay = day,
                        onShift = { day += it },
                        canGoForward = day < today(),
                    )
                    Spacer(Modifier.height(12.dp))
                    NumberField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = "Weight",
                        suffix = "kg",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    TimeRow(hour = hour, minute = minute, onPick = { h, m -> hour = h; minute = m })
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = note,
                        onValueChange = { note = it },
                        label = "Note (optional)",
                        modifier = Modifier.fillMaxWidth(),
                        imeAction = ImeAction.Done,
                    )
                    Spacer(Modifier.height(14.dp))
                    SaveButton(
                        text = "Save reading",
                        enabled = valid,
                        accent = seriesPalette.weight,
                        onClick = {
                            val timestamp = day.epochDayToLocalDate()
                                .atTime(hour, minute)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                            vm.add(parsed!!, timestamp, note)
                            weightText = ""
                            note = ""
                        },
                    )
                    if (weightText.isNotEmpty() && !valid) {
                        Spacer(Modifier.height(8.dp))
                        Hint("Enter a weight between 15 and 400 kg.")
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Trend",
                subtitle = "${daily.size} day(s) recorded",
            ) {
                ZoomableTimeChart(
                    series = listOf(ChartSeries("Weight", seriesPalette.weight, daily, SeriesKind.LINE)),
                    valueLabel = { it.oneDecimal() },
                    unitSuffix = " kg",
                    chartHeight = 270.dp,
                    defaultWindowDays = 60f,
                    emptyMessage = "Add your first reading to start the trend",
                )
            }
        }

        if (entries.size >= 2) {
            item {
                val newest = entries.first()
                val oldest = entries.last()
                val change = newest.weightKg - oldest.weightKg
                val span = newest.epochDay - oldest.epochDay
                SectionCard(title = "Change") {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (change >= 0) "+" else "−") + abs(change).oneDecimal() + " kg",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                "over ${span} day(s)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                (change / (span.coerceAtLeast(1L) / 7.0)).let {
                                    (if (it >= 0) "+" else "−") + abs(it).oneDecimal()
                                } + " kg",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                "per week",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "All readings") {
                Column {
                    if (entries.isEmpty()) {
                        Hint("Nothing recorded yet.")
                    }
                    entries.forEachIndexed { index, entry ->
                        val previous = entries.getOrNull(index + 1)
                        val delta = previous?.let { entry.weightKg - it.weightKg }
                        EntryRow(
                            title = entry.weightKg.oneDecimal() + " kg",
                            subtitle = entry.epochDay.dayLabel() + "  ·  " +
                                entry.timestamp.asTime() +
                                (entry.note?.let { "  ·  $it" } ?: ""),
                            trailing = delta?.let {
                                (if (it >= 0) "+" else "−") + abs(it).oneDecimal()
                            } ?: "—",
                            accent = seriesPalette.weight,
                            onDelete = { vm.delete(entry) },
                        )
                    }
                }
            }
        }
    }
}
