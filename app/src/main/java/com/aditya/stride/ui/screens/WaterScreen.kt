package com.aditya.stride.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.data.epochDayToLocalDate
import com.aditya.stride.data.today
import com.aditya.stride.ui.asTime
import com.aditya.stride.ui.components.ChartGuide
import com.aditya.stride.ui.components.ChartSeries
import com.aditya.stride.ui.components.Chip
import com.aditya.stride.ui.components.DayNavigator
import com.aditya.stride.ui.components.EntryRow
import com.aditya.stride.ui.components.GoalBar
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.NumberField
import com.aditya.stride.ui.components.SaveButton
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.SeriesKind
import com.aditya.stride.ui.components.TimeRow
import com.aditya.stride.ui.components.ZoomableTimeChart
import com.aditya.stride.ui.oneDecimal
import com.aditya.stride.ui.theme.seriesPalette
import com.aditya.stride.ui.vm.WaterViewModel
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun WaterScreen(onBack: () -> Unit) {
    val vm: WaterViewModel = viewModel()
    val day by vm.selectedDay.collectAsStateWithLifecycle()
    val entries by vm.dayEntries.collectAsStateWithLifecycle()
    val total by vm.dayTotal.collectAsStateWithLifecycle()
    val daily by vm.daily.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()

    val now = remember { LocalTime.now() }
    var hour by remember { mutableStateOf(now.hour) }
    var minute by remember { mutableStateOf(now.minute) }
    var litresText by remember { mutableStateOf("") }

    fun timestampFor(): Long = day.epochDayToLocalDate()
        .atTime(hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    val parsed = litresText.toDoubleOrNull()
    val valid = parsed != null && parsed > 0 && parsed <= 15

    ScreenFrame(
        title = "Water",
        subtitle = "In litres",
        onBack = onBack,
    ) {
        item {
            SectionCard(title = "Add water") {
                Column {
                    DayNavigator(
                        epochDay = day,
                        onShift = { vm.shiftDay(it) },
                        canGoForward = day < today(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            total.oneDecimal(),
                            style = MaterialTheme.typography.displaySmall,
                            color = seriesPalette.water,
                        )
                        Text(
                            " / ${profile.waterGoalL.oneDecimal()} L",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(30.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    GoalBar(
                        fraction = if (profile.waterGoalL > 0) {
                            (total / profile.waterGoalL).toFloat()
                        } else 0f,
                        accent = seriesPalette.water,
                    )

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Quick add",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.2 to "200 ml", 0.25 to "250 ml", 0.5 to "500 ml", 1.0 to "1 L")
                            .forEach { (amount, label) ->
                                Chip(
                                    label = label,
                                    onClick = { vm.add(amount, timestampFor()) },
                                )
                            }
                    }

                    Spacer(Modifier.height(16.dp))
                    NumberField(
                        value = litresText,
                        onValueChange = { litresText = it },
                        label = "Custom amount",
                        suffix = "L",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    TimeRow(hour = hour, minute = minute, onPick = { h, m -> hour = h; minute = m })
                    Spacer(Modifier.height(14.dp))
                    SaveButton(
                        text = "Add",
                        enabled = valid,
                        accent = seriesPalette.water,
                        onClick = {
                            vm.add(parsed!!, timestampFor())
                            litresText = ""
                        },
                    )
                }
            }
        }

        item {
            SectionCard(title = "Consistency", subtitle = "Litres per day against your target") {
                ZoomableTimeChart(
                    series = listOf(ChartSeries("Water", seriesPalette.water, daily, SeriesKind.BAR)),
                    valueLabel = { it.oneDecimal() },
                    unitSuffix = " L",
                    zeroBased = true,
                    guide = ChartGuide(profile.waterGoalL, "target", seriesPalette.water),
                    chartHeight = 260.dp,
                    defaultWindowDays = 28f,
                    emptyMessage = "Add water to start the chart",
                )
            }
        }

        item {
            SectionCard(title = "Entries for this day") {
                Column {
                    if (entries.isEmpty()) Hint("Nothing logged for this day.")
                    entries.forEach { entry ->
                        EntryRow(
                            title = entry.liters.oneDecimal() + " L",
                            subtitle = entry.timestamp.asTime(),
                            trailing = "",
                            accent = seriesPalette.water,
                            onDelete = { vm.delete(entry) },
                        )
                    }
                }
            }
        }
    }
}
