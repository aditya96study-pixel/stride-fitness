package com.aditya.stride.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Sex { MALE, FEMALE }

/** Multiplier applied to BMR to estimate daily expenditure before logged exercise. */
enum class ActivityLevel(val label: String, val factor: Double, val blurb: String) {
    SEDENTARY("Sedentary", 1.2, "Desk job, little movement"),
    LIGHT("Lightly active", 1.375, "Light exercise 1–3 days/week"),
    MODERATE("Moderately active", 1.55, "Exercise 3–5 days/week"),
    ACTIVE("Very active", 1.725, "Hard exercise 6–7 days/week"),
    ATHLETE("Extra active", 1.9, "Physical job or twice-daily training"),
}

data class Profile(
    val name: String = "",
    val heightCm: Double = 175.0,
    val age: Int = 30,
    val sex: Sex = Sex.MALE,
    /** Fallback weight used for calorie maths before any weight has been logged. */
    val fallbackWeightKg: Double = 70.0,
    val activityLevel: ActivityLevel = ActivityLevel.LIGHT,
    val waterGoalL: Double = 3.0,
    val calorieGoal: Int = 2200,
    val onboarded: Boolean = false,
    /** Pause the timer automatically when you stop moving during a run. */
    val autoPause: Boolean = true,
    /** Discard GPS fixes reported less accurate than this many metres. */
    val gpsAccuracyGateM: Float = 25f,
    val keepScreenOnDuringRun: Boolean = true,
) {
    /** Mifflin–St Jeor basal metabolic rate, kcal/day. */
    fun bmr(weightKg: Double): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return if (sex == Sex.MALE) base + 5.0 else base - 161.0
    }

    /** Total daily energy expenditure before any exercise you log explicitly. */
    fun tdee(weightKg: Double): Double = bmr(weightKg) * activityLevel.factor
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stride_profile")

class ProfileStore(private val context: Context) {

    private object Keys {
        val NAME = stringPreferencesKey("name")
        val HEIGHT = doublePreferencesKey("height_cm")
        val AGE = intPreferencesKey("age")
        val SEX = stringPreferencesKey("sex")
        val WEIGHT = doublePreferencesKey("fallback_weight_kg")
        val ACTIVITY = stringPreferencesKey("activity_level")
        val WATER_GOAL = doublePreferencesKey("water_goal_l")
        val CALORIE_GOAL = intPreferencesKey("calorie_goal")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val AUTO_PAUSE = booleanPreferencesKey("auto_pause")
        val ACCURACY_GATE = doublePreferencesKey("accuracy_gate")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }

    val profile: Flow<Profile> = context.dataStore.data.map { p ->
        Profile(
            name = p[Keys.NAME] ?: "",
            heightCm = p[Keys.HEIGHT] ?: 175.0,
            age = p[Keys.AGE] ?: 30,
            sex = runCatching { Sex.valueOf(p[Keys.SEX] ?: "MALE") }.getOrDefault(Sex.MALE),
            fallbackWeightKg = p[Keys.WEIGHT] ?: 70.0,
            activityLevel = runCatching {
                ActivityLevel.valueOf(p[Keys.ACTIVITY] ?: "LIGHT")
            }.getOrDefault(ActivityLevel.LIGHT),
            waterGoalL = p[Keys.WATER_GOAL] ?: 3.0,
            calorieGoal = p[Keys.CALORIE_GOAL] ?: 2200,
            onboarded = p[Keys.ONBOARDED] ?: false,
            autoPause = p[Keys.AUTO_PAUSE] ?: true,
            gpsAccuracyGateM = (p[Keys.ACCURACY_GATE] ?: 25.0).toFloat(),
            keepScreenOnDuringRun = p[Keys.KEEP_SCREEN_ON] ?: true,
        )
    }

    suspend fun save(profile: Profile) {
        context.dataStore.edit { p ->
            p[Keys.NAME] = profile.name
            p[Keys.HEIGHT] = profile.heightCm
            p[Keys.AGE] = profile.age
            p[Keys.SEX] = profile.sex.name
            p[Keys.WEIGHT] = profile.fallbackWeightKg
            p[Keys.ACTIVITY] = profile.activityLevel.name
            p[Keys.WATER_GOAL] = profile.waterGoalL
            p[Keys.CALORIE_GOAL] = profile.calorieGoal
            p[Keys.ONBOARDED] = profile.onboarded
            p[Keys.AUTO_PAUSE] = profile.autoPause
            p[Keys.ACCURACY_GATE] = profile.gpsAccuracyGateM.toDouble()
            p[Keys.KEEP_SCREEN_ON] = profile.keepScreenOnDuringRun
        }
    }

    suspend fun markOnboarded() {
        context.dataStore.edit { it[Keys.ONBOARDED] = true }
    }
}
