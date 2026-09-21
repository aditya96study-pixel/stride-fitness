package com.aditya.stride.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.aditya.stride.data.RunPoint
import com.aditya.stride.tracking.formatPace
import com.aditya.stride.ui.asClock
import com.aditya.stride.ui.asDateTime
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.NumberField
import com.aditya.stride.ui.components.RouteMap
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.StatTile
import com.aditya.stride.ui.components.TextField
import com.aditya.stride.ui.oneDecimal
import com.aditya.stride.ui.theme.seriesPalette
import com.aditya.stride.ui.theme.StatusCritical
import com.aditya.stride.ui.twoDecimals
import com.aditya.stride.ui.vm.RunHistoryViewModel
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun RunDetailScreen(runId: Long, onBack: () -> Unit) {
    val vm: RunHistoryViewModel = viewModel()
    val sessionFlow = remember(runId) { vm.session(runId) }
    val pointsFlow = remember(runId) { vm.points(runId) }
    val session by sessionFlow.collectAsStateWithLifecycle(initialValue = null)
    val points by pointsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var kcalText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(session?.id) {
        session?.let {
            kcalText = it.kcal.toString()
            note = it.note.orEmpty()
        }
    }

    val run = session
    if (run == null) {
        ScreenFrame(title = "Run", onBack = onBack) {
            item { Hint("This run is no longer available.") }
        }
        return
    }

    val splits = remember(points) { splitsFrom(points) }
    val avgPace = if (run.distanceM > 50) (run.movingTimeMs / 1000.0) / (run.distanceM / 1000.0)
    else null

    ScreenFrame(
        title = (run.distanceM / 1000.0).twoDecimals() + " km",
        subtitle = run.startTime.asDateTime(),
        onBack = onBack,
    ) {
        if (points.size >= 2) {
            item {
                SectionCard(title = "Route") {
                    RouteMap(
                        latitudes = points.map { it.lat },
                        longitudes = points.map { it.lon },
                        lineColor = seriesPalette.distance,
                    )
                }
            }
        }

        item {
            SectionCard(title = "Summary") {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            "Distance",
                            (run.distanceM / 1000.0).twoDecimals(),
                            "km",
                            seriesPalette.distance,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Moving",
                            run.movingTimeMs.asClock(),
                            accent = seriesPalette.distance,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Elapsed",
                            run.elapsedTimeMs.asClock(),
                            accent = seriesPalette.distance,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            "Avg pace",
                            avgPace?.let { formatPace(it) } ?: "--:--",
                            "/km",
                            seriesPalette.distance,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Avg speed",
                            (run.avgSpeedMps * 3.6).oneDecimal(),
                            "km/h",
                            seriesPalette.distance,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Top speed",
                            (run.maxSpeedMps * 3.6).oneDecimal(),
                            "km/h",
                            seriesPalette.distance,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            "Climb",
                            run.elevationGainM.roundToInt().toString(),
                            "m",
                            seriesPalette.calOut,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Calories",
                            run.kcal.toString(),
                            "kcal",
                            seriesPalette.calOut,
                            caption = if (run.kcal != run.kcalAuto) {
                                "edited from ${run.kcalAuto}"
                            } else "from pace, weight and climb",
                            modifier = Modifier.weight(2f),
                        )
                    }
                }
            }
        }

        if (splits.isNotEmpty()) {
            item {
                SectionCard(title = "Splits", subtitle = "Each completed kilometre") {
                    Column {
                        splits.forEach { (km, ms) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "km $km",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.width(58.dp),
                                )
                                Text(
                                    ms.asClock(),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatPace(ms / 1000.0) + " /km",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Adjust") {
                Column {
                    NumberField(
                        value = kcalText,
                        onValueChange = { kcalText = it },
                        label = "Calories counted",
                        suffix = "kcal",
                        decimal = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = note,
                        onValueChange = { note = it },
                        label = "Note",
                        modifier = Modifier.fillMaxWidth(),
                        imeAction = ImeAction.Done,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                kcalText.toIntOrNull()?.let { vm.overrideCalories(run, it) }
                                vm.setNote(run, note)
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Save") }
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.weight(1f),
                        ) { Text("Delete run", color = StatusCritical) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Hint(
                        "The calorie figure comes from the ACSM running equation using your " +
                            "pace, your logged weight and the climb. Override it if you prefer " +
                            "your watch's number."
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this run?") },
            text = { Text("The route and all its data will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(run)
                    onBack()
                }) { Text("Delete", color = StatusCritical) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Time for each completed kilometre, interpolated between the two GPS fixes that
 * straddle the kilometre mark so a split is not rounded to whichever fix landed
 * nearest.
 */
private fun splitsFrom(points: List<RunPoint>): List<Pair<Int, Long>> {
    if (points.size < 2) return emptyList()
    val result = mutableListOf<Pair<Int, Long>>()
    var targetKm = 1
    var previousCrossing = points.first().timestamp

    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        while (b.cumulativeM >= targetKm * 1000.0 && a.cumulativeM < targetKm * 1000.0) {
            val segment = b.cumulativeM - a.cumulativeM
            val fraction = if (segment > 0) {
                (targetKm * 1000.0 - a.cumulativeM) / segment
            } else 0.0
            val crossingTime =
                a.timestamp + ((b.timestamp - a.timestamp) * fraction).roundToLong()
            result += targetKm to (crossingTime - previousCrossing)
            previousCrossing = crossingTime
            targetKm++
        }
    }
    return result
}
