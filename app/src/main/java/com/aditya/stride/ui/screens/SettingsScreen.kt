package com.aditya.stride.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.BuildConfig
import com.aditya.stride.data.ThemeMode
import com.aditya.stride.export.CsvExporter
import com.aditya.stride.ui.components.Chip
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.SaveButton
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.theme.seriesPalette
import com.aditya.stride.ui.vm.SettingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenReminders: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: SettingsViewModel = viewModel()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()

    var exportMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = CsvExporter.writeTo(context, uri)
                exportMessage = if (ok) "Exported." else "Could not write that file."
            }
        }
    }

    ScreenFrame(title = "Settings", onBack = onBack) {
        item {
            SectionCard(title = "Profile") {
                LinkRow(
                    title = "Body metrics and targets",
                    subtitle = "Height, age, weight, water and calorie targets",
                    onClick = onOpenProfile,
                )
            }
        }

        item {
            SectionCard(title = "Appearance") {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            Chip(
                                label = mode.label,
                                onClick = { vm.setThemeMode(mode) },
                                selected = profile.themeMode == mode,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Run tracking") {
                Column {
                    ToggleRow(
                        title = "Auto-pause",
                        subtitle = "Stops the timer after 8 seconds standing still",
                        checked = profile.autoPause,
                        onChange = { vm.setAutoPause(it) },
                    )
                    ToggleRow(
                        title = "Keep screen on while running",
                        subtitle = "Only during a run",
                        checked = profile.keepScreenOnDuringRun,
                        onChange = { vm.setKeepScreenOn(it) },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "GPS accuracy limit — ${profile.gpsAccuracyGateM.roundToInt()} m",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Hint(
                        "Fixes reported less accurate than this are discarded. Lower is " +
                            "stricter; too low and a run under tree cover may record nothing."
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(12f, 18f, 25f, 40f).forEach { value ->
                            Chip(
                                label = "${value.roundToInt()} m",
                                onClick = { vm.setGpsGate(value) },
                                selected = profile.gpsAccuracyGateM == value,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Reminders",
                subtitle = if (reminders.isEmpty()) {
                    "None set"
                } else {
                    "${reminders.count { it.enabled }} active of ${reminders.size}"
                },
            ) {
                Column {
                    LinkRow(
                        title = "Manage reminders",
                        subtitle = "Times, days and what each one is for",
                        onClick = onOpenReminders,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Snooze length — ${profile.snoozeMinutes} min",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 30, 60).forEach { minutes ->
                            Chip(
                                label = "$minutes min",
                                onClick = { vm.setSnoozeMinutes(minutes) },
                                selected = profile.snoozeMinutes == minutes,
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Reminders arriving late? Allow alarms and reminders in system " +
                                "settings.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                AndroidSettings
                                                    .ACTION_APPLICATION_DETAILS_SETTINGS
                                            ).setData(
                                                android.net.Uri.parse(
                                                    "package:" + context.packageName
                                                )
                                            )
                                        )
                                    }
                                }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Your data", subtitle = "Held on this phone only") {
                Column {
                    SaveButton(
                        text = "Export everything as CSV",
                        enabled = true,
                        accent = seriesPalette.calOut,
                        onClick = { exportLauncher.launch(CsvExporter.suggestedFileName()) },
                    )
                    exportMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Hint(it)
                    }
                }
            }
        }

        item {
            SectionCard(title = "About") {
                Column {
                    Text(
                        "Stride ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(6.dp))
                    Hint("Map tiles © OpenStreetMap contributors.")
                }
            }
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
