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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aditya.stride.data.ActivityLevel
import com.aditya.stride.data.Profile
import com.aditya.stride.data.Sex
import com.aditya.stride.ui.components.Chip
import com.aditya.stride.ui.components.Hint
import com.aditya.stride.ui.components.NumberField
import com.aditya.stride.ui.components.SaveButton
import com.aditya.stride.ui.components.ScreenFrame
import com.aditya.stride.ui.components.SectionCard
import com.aditya.stride.ui.components.StatTile
import com.aditya.stride.ui.oneDecimal
import com.aditya.stride.ui.theme.StatusGood
import com.aditya.stride.ui.theme.seriesPalette
import com.aditya.stride.ui.vm.SettingsViewModel
import kotlin.math.roundToInt

/**
 * The typed state of this screen, as one value.
 *
 * Keeping the fields together is what makes "has anything changed?" a single comparison
 * against the stored profile, which is what the Save button's enabled state is. Held as
 * strings rather than numbers so a half-typed "17" is not silently read as 17 cm.
 */
private data class ProfileForm(
    val height: String,
    val age: String,
    val sex: Sex,
    val fallbackWeight: String,
    val activity: ActivityLevel,
    val waterGoal: String,
    val calorieGoal: String,
) {
    /** Ranges wide enough for anyone, narrow enough to catch a slipped decimal point. */
    private val heightCm: Double? get() = height.toDoubleOrNull()?.takeIf { it in 80.0..250.0 }
    private val ageYears: Int? get() = age.toIntOrNull()?.takeIf { it in 10..120 }
    private val weightKg: Double? get() = fallbackWeight.toDoubleOrNull()?.takeIf { it in 25.0..300.0 }
    private val waterL: Double? get() = waterGoal.toDoubleOrNull()?.takeIf { it in 0.5..10.0 }
    private val kcal: Int? get() = calorieGoal.toIntOrNull()?.takeIf { it in 800..6000 }

    /** Named so the screen can say which field is wrong instead of just refusing to save. */
    val problems: List<String>
        get() = buildList {
            if (heightCm == null) add("height between 80 and 250 cm")
            if (ageYears == null) add("age between 10 and 120")
            if (weightKg == null) add("starting weight between 25 and 300 kg")
            if (waterL == null) add("water target between 0.5 and 10 L")
            if (kcal == null) add("calorie target between 800 and 6000")
        }

    val isValid: Boolean get() = problems.isEmpty()

    /**
     * Applied onto the stored profile rather than built from scratch, so settings this
     * screen does not show — the theme, the GPS gate — are carried through untouched.
     */
    fun toProfile(base: Profile): Profile = base.copy(
        heightCm = heightCm ?: base.heightCm,
        age = ageYears ?: base.age,
        sex = sex,
        fallbackWeightKg = weightKg ?: base.fallbackWeightKg,
        activityLevel = activity,
        waterGoalL = waterL ?: base.waterGoalL,
        calorieGoal = kcal ?: base.calorieGoal,
    )

    companion object {
        fun from(p: Profile) = ProfileForm(
            height = p.heightCm.oneDecimal(),
            age = p.age.toString(),
            sex = p.sex,
            fallbackWeight = p.fallbackWeightKg.oneDecimal(),
            activity = p.activityLevel,
            waterGoal = p.waterGoalL.oneDecimal(),
            calorieGoal = p.calorieGoal.toString(),
        )
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val latestWeight by vm.latestWeight.collectAsStateWithLifecycle()

    var form by remember { mutableStateOf(ProfileForm.from(profile)) }
    // What the form was last seeded from. Without it, every profile write anywhere in the
    // app re-fires the effect below and wipes whatever is half-typed here.
    var seededFrom by remember { mutableStateOf(profile) }
    var justSaved by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        // Re-seed only while the form still matches what it was seeded from: an untouched
        // form should pick up a change made elsewhere, an edited one must not lose it.
        if (form == ProfileForm.from(seededFrom)) form = ProfileForm.from(profile)
        seededFrom = profile
    }

    val stored = remember(profile) { ProfileForm.from(profile) }
    val dirty = form != stored
    // "Saved" is only true until the next edit; clearing it in an effect rather than
    // during composition keeps composition free of side effects.
    LaunchedEffect(dirty) { if (dirty) justSaved = false }

    val weightForMaths = latestWeight?.weightKg ?: profile.fallbackWeightKg
    val bmr = profile.bmr(weightForMaths)
    val tdee = profile.tdee(weightForMaths)

    ScreenFrame(
        title = "Profile",
        subtitle = "Body metrics and daily targets",
        onBack = onBack,
    ) {
        item {
            SectionCard(title = "Energy estimates") {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile(
                            "Resting (BMR)",
                            bmr.roundToInt().toString(),
                            "kcal/day",
                            seriesPalette.calOut,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Maintenance",
                            tdee.roundToInt().toString(),
                            "kcal/day",
                            seriesPalette.calIn,
                            caption = profile.activityLevel.label,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Hint(
                        "Calculated from ${weightForMaths.oneDecimal()} kg. Calories out on " +
                            "the Today screen are resting metabolism plus what you logged, so " +
                            "exercise is never counted twice."
                    )
                }
            }
        }

        item {
            SectionCard(title = "Body metrics") {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(
                            value = form.height,
                            onValueChange = { form = form.copy(height = it) },
                            label = "Height",
                            suffix = "cm",
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = form.age,
                            onValueChange = { form = form.copy(age = it) },
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
                        Sex.entries.forEach { value ->
                            Chip(
                                label = if (value == Sex.MALE) "Male" else "Female",
                                onClick = { form = form.copy(sex = value) },
                                selected = form.sex == value,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    NumberField(
                        value = form.fallbackWeight,
                        onValueChange = { form = form.copy(fallbackWeight = it) },
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
                                onClick = { form = form.copy(activity = level) },
                                selected = form.activity == level,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Hint(form.activity.blurb)
                }
            }
        }

        item {
            SectionCard(title = "Daily targets") {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(
                            value = form.waterGoal,
                            onValueChange = { form = form.copy(waterGoal = it) },
                            label = "Water",
                            suffix = "L",
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = form.calorieGoal,
                            onValueChange = { form = form.copy(calorieGoal = it) },
                            label = "Calories",
                            suffix = "kcal",
                            decimal = false,
                            modifier = Modifier.weight(1f),
                            imeAction = ImeAction.Done,
                        )
                    }
                }
            }
        }

        item {
            Column {
                SaveButton(
                    // The button states the situation instead of looking identical whether
                    // or not there is anything to save.
                    text = when {
                        dirty && !form.isValid -> "Check the highlighted values"
                        dirty -> "Save changes"
                        justSaved -> "Saved"
                        else -> "No changes to save"
                    },
                    enabled = dirty && form.isValid,
                    accent = if (dirty && form.isValid) {
                        StatusGood
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    onClick = {
                        vm.saveProfile(form.toProfile(profile))
                        justSaved = true
                    },
                )
                if (dirty && !form.isValid) {
                    Spacer(Modifier.height(8.dp))
                    Hint("Needs " + form.problems.joinToString("; ") + ".")
                }
                if (dirty) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Discard changes", onClick = { form = stored })
                    }
                }
            }
        }
    }
}
