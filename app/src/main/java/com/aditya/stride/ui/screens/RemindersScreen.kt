package com.aditya.stride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.data.Reminder
import com.aditya.stride.data.ReminderKind
import com.aditya.stride.notify.ReminderScheduler
import com.aditya.stride.ui.components.Chip
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.SaveButton
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.TextField
import com.aditya.stride.ui.components.TimePickerDialog
import com.aditya.stride.ui.formatHourMinute
import com.aditya.stride.ui.theme.seriesPalette
import com.aditya.stride.ui.theme.StatusGood

private val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
private fun accentFor(kind: ReminderKind) = when (kind) {
    ReminderKind.FOOD -> seriesPalette.calIn
    ReminderKind.WATER -> seriesPalette.water
    ReminderKind.WEIGHT -> seriesPalette.weight
    ReminderKind.EXERCISE -> seriesPalette.calOut
    ReminderKind.GENERAL -> StatusGood
}

@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val vm: com.aditya.stride.ui.vm.SettingsViewModel = viewModel()
    val reminders by vm.reminders.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Reminder?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    ScreenFrame(
        title = "Reminders",
        subtitle = "Your own times, as many as you like",
        onBack = onBack,
    ) {
        item {
            SectionCard(title = "Add a reminder") {
                Column {
                    Hint(
                        "Choose what it should nudge you about, the time, and which days. " +
                            "Tapping the notification opens the right screen straight away."
                    )
                    Spacer(Modifier.height(12.dp))
                    SaveButton(
                        text = "New reminder",
                        enabled = true,
                        accent = StatusGood,
                        onClick = {
                            editing = null
                            showEditor = true
                        },
                    )
                }
            }
        }

        item {
            SectionCard(title = "Your reminders") {
                Column {
                    if (reminders.isEmpty()) {
                        Hint("None yet.")
                    }
                    reminders.forEach { reminder ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    editing = reminder
                                    showEditor = true
                                }
                                .padding(vertical = 10.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        accentFor(
                                            runCatching { ReminderKind.valueOf(reminder.kind) }
                                                .getOrDefault(ReminderKind.GENERAL)
                                        )
                                    )
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    formatHourMinute(reminder.hour, reminder.minute) +
                                        "  ·  " + reminder.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    ReminderScheduler.daysLabel(reminder.daysMask),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = reminder.enabled,
                                onCheckedChange = { vm.toggleReminder(reminder, it) },
                            )
                            IconButton(onClick = { vm.deleteReminder(reminder) }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Delete reminder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Suggested sets") {
                Column {
                    Hint("One tap to create a sensible group. Edit or delete any of them after.")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("3 meal nudges", {
                            listOf(9 to "Log breakfast", 14 to "Log lunch", 21 to "Log dinner")
                                .forEach { (hour, label) ->
                                    vm.upsertReminder(
                                        Reminder(
                                            label = label,
                                            kind = ReminderKind.FOOD.name,
                                            hour = hour,
                                            minute = 0,
                                        )
                                    )
                                }
                        })
                        Chip("Water ×4", {
                            listOf(10, 13, 16, 19).forEach { hour ->
                                vm.upsertReminder(
                                    Reminder(
                                        label = "Drink water",
                                        kind = ReminderKind.WATER.name,
                                        hour = hour,
                                        minute = 30,
                                    )
                                )
                            }
                        })
                        Chip("Morning weigh-in", {
                            vm.upsertReminder(
                                Reminder(
                                    label = "Weigh yourself",
                                    kind = ReminderKind.WEIGHT.name,
                                    hour = 7,
                                    minute = 0,
                                )
                            )
                        })
                    }
                }
            }
        }
    }

    if (showEditor) {
        ReminderEditor(
            existing = editing,
            onDismiss = { showEditor = false },
            onSave = {
                vm.upsertReminder(it)
                showEditor = false
            },
        )
    }
}

@Composable
private fun ReminderEditor(
    existing: Reminder?,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
) {
    var kind by remember {
        mutableStateOf(
            existing?.let {
                runCatching { ReminderKind.valueOf(it.kind) }.getOrDefault(ReminderKind.FOOD)
            } ?: ReminderKind.FOOD
        )
    }
    var label by remember { mutableStateOf(existing?.label ?: ReminderKind.FOOD.title) }
    var hour by remember { mutableStateOf(existing?.hour ?: 9) }
    var minute by remember { mutableStateOf(existing?.minute ?: 0) }
    var daysMask by remember { mutableStateOf(existing?.daysMask ?: 127) }
    var showTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New reminder" else "Edit reminder") },
        text = {
            Column {
                Text(
                    "Remind me to",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderKind.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { option ->
                                Chip(
                                    label = option.title,
                                    onClick = {
                                        val hadDefaultLabel = label == kind.title
                                        kind = option
                                        if (hadDefaultLabel) label = option.title
                                    },
                                    selected = kind == option,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                TextField(
                    value = label,
                    onValueChange = { label = it },
                    label = "Notification text",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showTime = true }
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatHourMinute(hour, minute),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "Days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until 7).forEach { index ->
                        val bit = 1 shl index
                        val on = daysMask and bit != 0
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (on) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                )
                                .clickable { daysMask = daysMask xor bit },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                dayLetters[index],
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && daysMask != 0,
                onClick = {
                    onSave(
                        Reminder(
                            id = existing?.id ?: 0L,
                            label = label.trim(),
                            kind = kind.name,
                            hour = hour,
                            minute = minute,
                            daysMask = daysMask,
                            enabled = existing?.enabled ?: true,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showTime) {
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showTime = false },
            onConfirm = { h, m -> hour = h; minute = m; showTime = false },
        )
    }
}
