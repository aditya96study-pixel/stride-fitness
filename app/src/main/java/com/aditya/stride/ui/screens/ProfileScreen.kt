package com.aditya.stride.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.data.ActivityLevel
import com.aditya.stride.data.Sex
import com.aditya.stride.export.CsvExporter
import com.aditya.stride.ui.components.Chip
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.NumberField
import com.aditya.stride.ui.components.SaveButton
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.StatTile
import com.aditya.stride.ui.components.TextField
import com.aditya.stride.ui.oneDecimal
import com.aditya.stride.ui.theme.SeriesCalIn
import com.aditya.stride.ui.theme.SeriesCalOut
import com.aditya.stride.ui.theme.SeriesWater
import com.aditya.stride.ui.theme.StatusGood
import com.aditya.stride.ui.vm.SettingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(onOpenReminders: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: SettingsViewModel = viewModel()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val latestWeight by vm.latestWeight.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(Sex.MALE) }
    var fallbackWeight by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf(ActivityLevel.LIGHT) }
    var waterGoal by remember { mutableStateOf("") }
    var calorieGoal by remember { mutableStateOf("") }
    var autoPause by remember { mutableStateOf(true) }
    var keepScreenOn by remember { mutableStateOf(true) }
    var accuracyGate by remember { mutableStateOf(25f) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile) {
        name = profile.name
        height = profile.heightCm.oneDecimal()
        age = profile.age.toString()
        sex = profile.sex
        fallbackWeight = profile.fallbackWeightKg.oneDecimal()
        activity = profile.activityLevel
        waterGoal = profile.waterGoalL.oneDecimal()
        calorieGoal = profile.calorieGoal.toString()
        autoPause = profile.autoPause
        keepScreenOn = profile.keepScreenOnDuringRun
        accuracyGate = profile.gpsAccuracyGateM
    }

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

    val weightForMaths = latestWeight?.weightKg ?: profile.fallbackWeightKg
    val bmr = profile.bmr(weightForMaths)
    val tdee = profile.tdee(weightForMaths)

    ScreenFrame(
        title = "You",
        subtitle = "Used for the calorie maths",
    ) {
        item {
            SectionCard(title = "Energy estimates", subtitle = "From your details and latest weight") {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            "Resting (BMR)",
                            bmr.roundToInt().toString(),
                            "kcal/day",
                            SeriesCalOut,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Maintenance",
                            tdee.roundToInt().toString(),
                            "kcal/day",
                            SeriesCalIn,
                            caption = profile.activityLevel.label,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Hint(
                        "Mifflin–St Jeor for resting metabolism, using ${weightForMaths.oneDecimal()} kg. " +
                            "On the Today screen, calories out are resting × 1.2 plus whatever " +
                            "you actually logged — exercise is never counted twice."
                    )
                }
            }
        }

        item {
            SectionCard(title = "Your details") {
                Column {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Name (optional)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(
                            value = height,
                            onValueChange = { height = it },
                            label = "Height",
                            suffix = "cm",
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = age,
                            onValueChange = { age = it },
                            label = "Age",
                            suffix = "yrs",
                            decimal = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Sex — changes the BMR formula",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Male", { sex = Sex.MALE }, selected = sex == Sex.MALE)
                        Chip("Female", { sex = Sex.FEMALE }, selected = sex == Sex.FEMALE)
                    }

                    Spacer(Modifier.height(14.dp))
                    NumberField(
                        value = fallbackWeight,
                        onValueChange = { fallbackWeight = it },
                        label = "Starting weight (used until you log one)",
                        suffix = "kg",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Everyday activity level",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ActivityLevel.entries.forEach { level ->
                            Chip(
                                label = level.label,
                                onClick = { activity = level },
                                selected = activity == level,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Hint(activity.blurb)

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(
                            value = waterGoal,
                            onValueChange = { waterGoal = it },
                            label = "Water target",
                            suffix = "L",
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = calorieGoal,
                            onValueChange = { calorieGoal = it },
                            label = "Calorie target",
                            suffix = "kcal",
                            decimal = false,
                            modifier = Modifier.weight(1f),
                            imeAction = ImeAction.Done,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    SaveButton(
                        text = "Save details",
                        enabled = true,
                        accent = StatusGood,
                        onClick = {
                            vm.saveProfile(
                                profile.copy(
                                    name = name.trim(),
                                    heightCm = height.toDoubleOrNull() ?: profile.heightCm,
                                    age = age.toIntOrNull() ?: profile.age,
                                    sex = sex,
                                    fallbackWeightKg = fallbackWeight.toDoubleOrNull()
                                        ?: profile.fallbackWeightKg,
                                    activityLevel = activity,
                                    waterGoalL = waterGoal.toDoubleOrNull() ?: profile.waterGoalL,
                                    calorieGoal = calorieGoal.toIntOrNull() ?: profile.calorieGoal,
                                )
                            )
                        },
                    )
                }
            }
        }

        item {
            SectionCard(title = "Run tracking") {
                Column {
                    ToggleRow(
                        title = "Auto-pause",
                        subtitle = "Stops the timer after 8 seconds standing still",
                        checked = autoPause,
                        onChange = {
                            autoPause = it
                            vm.saveProfile(profile.copy(autoPause = it))
                        },
                    )
                    ToggleRow(
                        title = "Keep screen on while running",
                        subtitle = "Only during a run",
                        checked = keepScreenOn,
                        onChange = {
                            keepScreenOn = it
                            vm.saveProfile(profile.copy(keepScreenOnDuringRun = it))
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "GPS accuracy limit — ${accuracyGate.roundToInt()} m",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Hint(
                        "Fixes reported less accurate than this are discarded. Lower is " +
                            "stricter and more accurate; too low and a run under tree cover " +
                            "may record nothing."
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(12f, 18f, 25f, 40f).forEach { value ->
                            Chip(
                                label = "${value.roundToInt()} m",
                                onClick = {
                                    accuracyGate = value
                                    vm.saveProfile(profile.copy(gpsAccuracyGateM = value))
                                },
                                selected = accuracyGate == value,
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
                    Hint(
                        "Pick your own times to be nudged about food, water, weight or " +
                            "workouts. These are plain alarms — nothing stays resident."
                    )
                    Spacer(Modifier.height(12.dp))
                    SaveButton(
                        text = "Manage reminders",
                        enabled = true,
                        accent = SeriesWater,
                        onClick = onOpenReminders,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "If reminders arrive late, allow alarms and reminders for Stride " +
                                "in system settings.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .setData(
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
            SectionCard(title = "Your data", subtitle = "Everything is stored on this phone only") {
                Column {
                    SaveButton(
                        text = "Export everything as CSV",
                        enabled = true,
                        accent = SeriesCalOut,
                        onClick = { exportLauncher.launch(CsvExporter.suggestedFileName()) },
                    )
                    exportMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Hint(it)
                    }
                    Spacer(Modifier.height(10.dp))
                    Hint(
                        "One file with every weight reading, meal, glass of water, workout " +
                            "and run. Nothing is sent anywhere — the app has no account and " +
                            "no server."
                    )
                }
            }
        }

        item {
            SectionCard(title = "About") {
                Hint(
                    "Stride 1.0 — built for Aditya. Distance and pace come from the fused " +
                        "location provider with Kalman smoothing; calories from the ACSM " +
                        "walking and running equations. Map tiles by OpenStreetMap " +
                        "contributors."
                )
            }
        }
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
