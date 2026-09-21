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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.aditya.stride.export.ImportResult
import com.aditya.stride.ui.components.Chip
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.SaveButton
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.theme.StatusCritical
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
    val pendingImport by vm.pendingImport.collectAsStateWithLifecycle()
    val importMessage by vm.importMessage.collectAsStateWithLifecycle()
    val existingCount by vm.existingCount.collectAsStateWithLifecycle()

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

    // Any readable text file may be a Stride export, and some file pickers report a CSV
    // as text/plain or with no type at all, so the filter stays wide and the parser
    // decides.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.readImport(uri) }

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

                    Spacer(Modifier.height(14.dp))
                    SaveButton(
                        text = "Import from CSV",
                        enabled = true,
                        accent = seriesPalette.water,
                        onClick = {
                            importLauncher.launch(
                                arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
                            )
                        },
                    )
                    importMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Hint(it)
                    }
                    Spacer(Modifier.height(8.dp))
                    Hint(
                        "Importing replaces everything currently in the app. Route maps are " +
                            "not part of the file, so restored sessions keep every figure but " +
                            "lose their map."
                    )
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

    pendingImport?.let { result ->
        ImportDialog(
            result = result,
            existingCount = existingCount,
            onDismiss = { vm.dismissImport() },
            onConfirm = {
                (result as? ImportResult.Ready)?.let { vm.applyImport(it.bundle) }
                    ?: vm.dismissImport()
            },
        )
    }
}

@Composable
private fun ImportDialog(
    result: ImportResult,
    existingCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    when (result) {
        is ImportResult.Rejected -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("That file was not imported") },
            text = {
                Column {
                    Text(result.reason)
                    if (result.errors.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Hint(result.errors.firstFiveLines())
                    }
                    Spacer(Modifier.height(10.dp))
                    Hint("Nothing in the app has been changed.")
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )

        is ImportResult.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Replace everything?") },
            text = {
                Column {
                    // Both numbers, so the size of what is being given up is visible.
                    Text(
                        "This replaces $existingCount existing " +
                            (if (existingCount == 1) "entry" else "entries") +
                            " with ${result.bundle.entryCount} from the file. It cannot be undone."
                    )
                    Spacer(Modifier.height(10.dp))
                    Hint("GPS routes are not stored in a CSV, so restored sessions have no map.")
                    if (result.bundle.legacy) {
                        Spacer(Modifier.height(8.dp))
                        Hint(
                            "This is an export from an older version. Its runs keep distance, " +
                                "time and calories, but some figures are read back from text " +
                                "and are approximate."
                        )
                    }
                    if (result.errors.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Hint(
                            "${result.errors.size} " +
                                (if (result.errors.size == 1) "row" else "rows") +
                                " could not be read and will be skipped:\n" +
                                result.errors.firstFiveLines()
                        )
                    }
                    if (result.ignored > 0) {
                        Spacer(Modifier.height(8.dp))
                        Hint("${result.ignored} rows of a kind this version does not know were ignored.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text("Replace", color = StatusCritical) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

private fun List<com.aditya.stride.export.RowError>.firstFiveLines(): String {
    val shown = take(5).joinToString("\n") { "line ${it.line}: ${it.reason}" }
    return if (size > 5) "$shown\n… and ${size - 5} more" else shown
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
