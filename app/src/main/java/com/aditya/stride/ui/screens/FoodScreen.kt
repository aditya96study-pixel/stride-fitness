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
import com.aditya.stride.ui.components.TextField
import com.aditya.stride.ui.components.TimeRow
import com.aditya.stride.ui.components.ZoomableTimeChart
import com.aditya.stride.ui.theme.seriesPalette
import com.aditya.stride.ui.vm.FoodViewModel
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun FoodScreen(onBack: () -> Unit) {
    val vm: FoodViewModel = viewModel()
    val day by vm.selectedDay.collectAsStateWithLifecycle()
    val entries by vm.dayEntries.collectAsStateWithLifecycle()
    val total by vm.dayTotal.collectAsStateWithLifecycle()
    val daily by vm.daily.collectAsStateWithLifecycle()
    val recentNames by vm.recentNames.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()

    val now = remember { LocalTime.now() }
    var hour by remember { mutableStateOf(now.hour) }
    var minute by remember { mutableStateOf(now.minute) }
    var name by remember { mutableStateOf("") }
    var kcalText by remember { mutableStateOf("") }

    val kcal = kcalText.toIntOrNull()
    val valid = kcal != null && kcal > 0 && kcal < 20000

    ScreenFrame(
        title = "Food",
        subtitle = "Calories you have eaten",
        onBack = onBack,
    ) {
        item {
            SectionCard(title = "Add a meal") {
                Column {
                    DayNavigator(
                        epochDay = day,
                        onShift = { vm.shiftDay(it) },
                        canGoForward = day < today(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            total.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            color = seriesPalette.calIn,
                        )
                        Text(
                            " / ${profile.calorieGoal} kcal",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(30.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    GoalBar(
                        fraction = if (profile.calorieGoal > 0) {
                            total / profile.calorieGoal.toFloat()
                        } else 0f,
                        accent = seriesPalette.calIn,
                    )

                    Spacer(Modifier.height(16.dp))
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "What did you eat?",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (recentNames.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            recentNames.take(12).forEach { previous ->
                                Chip(label = previous, onClick = { name = previous })
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        value = kcalText,
                        onValueChange = { kcalText = it },
                        label = "Calories",
                        suffix = "kcal",
                        decimal = false,
                        modifier = Modifier.fillMaxWidth(),
                        imeAction = ImeAction.Done,
                    )
                    Spacer(Modifier.height(10.dp))
                    TimeRow(hour = hour, minute = minute, onPick = { h, m -> hour = h; minute = m })
                    Spacer(Modifier.height(14.dp))
                    SaveButton(
                        text = "Add meal",
                        enabled = valid,
                        accent = seriesPalette.calIn,
                        onClick = {
                            val timestamp = day.epochDayToLocalDate()
                                .atTime(hour, minute)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                            vm.add(name, kcal!!, timestamp, null)
                            name = ""
                            kcalText = ""
                        },
                    )
                }
            }
        }

        item {
            SectionCard(title = "Meals on this day") {
                Column {
                    if (entries.isEmpty()) Hint("Nothing logged for this day.")
                    entries.forEach { entry ->
                        EntryRow(
                            title = entry.name,
                            subtitle = entry.timestamp.asTime(),
                            trailing = "${entry.kcal} kcal",
                            accent = seriesPalette.calIn,
                            onDelete = { vm.delete(entry) },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Daily intake", subtitle = "Total calories per day") {
                ZoomableTimeChart(
                    series = listOf(ChartSeries("Eaten", seriesPalette.calIn, daily, SeriesKind.BAR)),
                    valueLabel = { it.roundToInt().toString() },
                    unitSuffix = " kcal",
                    zeroBased = true,
                    guide = ChartGuide(
                        profile.calorieGoal.toDouble(),
                        "target",
                        seriesPalette.calIn,
                    ),
                    chartHeight = 260.dp,
                    defaultWindowDays = 21f,
                    emptyMessage = "Add a meal to start the chart",
                )
            }
        }
    }
}
