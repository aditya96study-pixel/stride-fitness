package com.aditya.stride.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.tracking.RunStatus
import com.aditya.stride.tracking.RunTracker
import com.aditya.stride.tracking.RunTrackingService
import com.aditya.stride.tracking.formatPace
import com.aditya.stride.ui.asClock
import com.aditya.stride.ui.dayLabel
import com.aditya.stride.ui.components.BigMetric
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.RouteCanvas
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.StatTile
import com.aditya.stride.ui.oneDecimal
import com.aditya.stride.ui.theme.SeriesCalOut
import com.aditya.stride.ui.theme.SeriesDistance
import com.aditya.stride.ui.theme.StatusCritical
import com.aditya.stride.ui.theme.StatusGood
import com.aditya.stride.ui.theme.StatusWarning
import com.aditya.stride.ui.twoDecimals
import com.aditya.stride.ui.vm.RunHistoryViewModel
import kotlin.math.roundToInt

@Composable
fun RunScreen(
    onOpenRuns: () -> Unit,
    onOpenRun: (Long) -> Unit,
) {
    val context = LocalContext.current
    val vm: RunHistoryViewModel = viewModel()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val state by RunTracker.state.collectAsStateWithLifecycle()
    val runs by vm.runs.collectAsStateWithLifecycle()

    var permissionDenied by remember { mutableStateOf(false) }

    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            permissionDenied = false
            RunTrackingService.send(context, RunTrackingService.ACTION_START)
        } else {
            permissionDenied = true
        }
    }

    fun hasLocation(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // A finished run opens straight into its summary.
    LaunchedEffect(state.savedRunId) {
        val id = state.savedRunId
        if (id != null) {
            RunTracker.clearSavedId()
            onOpenRun(id)
        }
    }

    // Keep the display awake only while actually tracking.
    KeepScreenOn(enabled = state.isActive && profile.keepScreenOnDuringRun)

    ScreenFrame(
        title = "Run",
        subtitle = when (state.status) {
            RunStatus.TRACKING -> "Recording"
            RunStatus.PAUSED -> if (state.autoPaused) "Auto-paused" else "Paused"
            RunStatus.SAVING -> "Saving…"
            RunStatus.IDLE -> "GPS tracked distance, pace and calories"
        },
        actions = {
            TextButton(onClick = onOpenRuns) {
                Text("History", style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        item {
            SectionCard {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        BigMetric(
                            value = state.distanceKm.twoDecimals(),
                            label = "kilometres",
                            accent = SeriesDistance,
                        )
                        BigMetric(
                            value = state.movingTimeMs.asClock(),
                            label = "moving time",
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            label = "Pace now",
                            value = state.currentPaceSecPerKm?.let { formatPace(it) } ?: "--:--",
                            unit = "/km",
                            accent = SeriesDistance,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Avg pace",
                            value = state.avgPaceSecPerKm?.let { formatPace(it) } ?: "--:--",
                            unit = "/km",
                            accent = SeriesDistance,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Speed",
                            value = (state.currentSpeedMps * 3.6f).toDouble().oneDecimal(),
                            unit = "km/h",
                            accent = SeriesDistance,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            label = "Calories",
                            value = state.kcal.roundToInt().toString(),
                            unit = "kcal",
                            accent = SeriesCalOut,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Climb",
                            value = state.elevationGainM.roundToInt().toString(),
                            unit = "m",
                            accent = SeriesCalOut,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Elapsed",
                            value = state.elapsedTimeMs.asClock(),
                            accent = SeriesCalOut,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item { GpsQualityRow(state.gpsAccuracyM, state.fixCount, state.rejectedFixes, state.isActive) }

        item {
            Column {
                when (state.status) {
                    RunStatus.IDLE -> {
                        Button(
                            onClick = {
                                if (hasLocation()) {
                                    RunTrackingService.send(
                                        context, RunTrackingService.ACTION_START
                                    )
                                } else {
                                    launcher.launch(permissions)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SeriesDistance,
                                contentColor = androidx.compose.ui.graphics.Color.White,
                            ),
                        ) {
                            Text("Start run", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(10.dp))
                        Hint(
                            "Location is requested only now, and only while the run is " +
                                "recording. Nothing runs in the background once you finish."
                        )
                        if (permissionDenied) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Location permission is needed to measure distance. " +
                                    "Allow it from Settings › Apps › Stride › Permissions.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StatusCritical,
                            )
                        }
                    }

                    RunStatus.TRACKING, RunStatus.PAUSED -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = {
                                    RunTrackingService.send(
                                        context,
                                        if (state.status == RunStatus.PAUSED) {
                                            RunTrackingService.ACTION_RESUME
                                        } else {
                                            RunTrackingService.ACTION_PAUSE
                                        },
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Text(
                                    if (state.status == RunStatus.PAUSED) "Resume" else "Pause",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Button(
                                onClick = {
                                    RunTrackingService.send(
                                        context, RunTrackingService.ACTION_FINISH
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SeriesDistance,
                                    contentColor = androidx.compose.ui.graphics.Color.White,
                                ),
                            ) {
                                Text("Finish", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                RunTrackingService.send(
                                    context, RunTrackingService.ACTION_DISCARD
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp),
                        ) {
                            Text("Discard", color = StatusCritical)
                        }
                    }

                    RunStatus.SAVING -> {
                        Text(
                            "Saving your run…",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        if (state.points.size >= 2) {
            item {
                SectionCard(title = "Route so far") {
                    RouteCanvas(
                        latitudes = state.points.map { it.lat },
                        longitudes = state.points.map { it.lon },
                        color = SeriesDistance,
                    )
                }
            }
        }

        if (state.splits.isNotEmpty()) {
            item {
                SectionCard(title = "Splits", subtitle = "Time for each kilometre") {
                    Column {
                        state.splits.forEach { split ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "km ${split.km}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.width(60.dp),
                                )
                                Text(
                                    split.durationMs.asClock(),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatPace(split.durationMs / 1000.0) + " /km",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.status == RunStatus.IDLE && runs.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Recent runs",
                    trailing = {
                        TextButton(onClick = onOpenRuns) { Text("See all") }
                    },
                ) {
                    Column {
                        runs.take(4).forEach { run ->
                            com.aditya.stride.ui.components.EntryRow(
                                title = (run.distanceM / 1000.0).twoDecimals() + " km",
                                subtitle = run.epochDay.dayLabel() + "  ·  " +
                                    run.movingTimeMs.asClock(),
                                trailing = "${run.kcal} kcal",
                                accent = SeriesDistance,
                                onClick = { onOpenRun(run.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GpsQualityRow(
    accuracyM: Float?,
    fixes: Int,
    rejected: Int,
    active: Boolean,
) {
    val (label, colour) = when {
        !active -> "GPS idle" to MaterialTheme.colorScheme.onSurfaceVariant
        accuracyM == null -> "Acquiring satellites…" to StatusWarning
        accuracyM <= 8f -> "Strong signal · ±${accuracyM.roundToInt()} m" to StatusGood
        accuracyM <= 16f -> "Good signal · ±${accuracyM.roundToInt()} m" to StatusGood
        accuracyM <= 25f -> "Weak signal · ±${accuracyM.roundToInt()} m" to StatusWarning
        else -> "Poor signal · ±${accuracyM.roundToInt()} m" to StatusCritical
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(colour)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (active && fixes > 0) {
            Text(
                "  ·  $fixes fixes used, $rejected discarded",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        var candidate: android.content.Context? = context
        while (candidate is android.content.ContextWrapper && candidate !is Activity) {
            candidate = candidate.baseContext
        }
        val window = (candidate as? Activity)?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
