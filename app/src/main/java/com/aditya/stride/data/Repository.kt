package com.aditya.stride.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Single access point for everything stored on the device. */
class Repository(context: Context) {

    private val db = AppDatabase.get(context)
    val weight = db.weightDao()
    val meals = db.mealDao()
    val water = db.waterDao()
    val exercise = db.exerciseDao()
    val runs = db.runDao()
    val reminders = db.reminderDao()
    val profileStore = ProfileStore(context.applicationContext)

    val profile: Flow<Profile> get() = profileStore.profile

    // ---------- writes ----------

    suspend fun addWeight(weightKg: Double, timestamp: Long, note: String? = null) =
        weight.insert(
            WeightEntry(
                epochDay = timestamp.toEpochDay(),
                timestamp = timestamp,
                weightKg = weightKg,
                note = note?.takeIf { it.isNotBlank() },
            )
        )

    suspend fun addMeal(name: String, kcal: Int, timestamp: Long, note: String? = null) =
        meals.insert(
            MealEntry(
                epochDay = timestamp.toEpochDay(),
                timestamp = timestamp,
                name = name.ifBlank { "Meal" },
                kcal = kcal,
                note = note?.takeIf { it.isNotBlank() },
            )
        )

    suspend fun addWater(liters: Double, timestamp: Long) =
        water.insert(
            WaterEntry(
                epochDay = timestamp.toEpochDay(),
                timestamp = timestamp,
                liters = liters,
            )
        )

    suspend fun addExercise(
        activity: String,
        kcal: Int,
        durationMin: Int,
        timestamp: Long,
        note: String? = null,
    ) = exercise.insert(
        ExerciseEntry(
            epochDay = timestamp.toEpochDay(),
            timestamp = timestamp,
            activity = activity.ifBlank { "Workout" },
            kcal = kcal,
            durationMin = durationMin,
            note = note?.takeIf { it.isNotBlank() },
        )
    )

    /** Weight used for energy maths: most recent logged reading, else the profile fallback. */
    suspend fun effectiveWeightKg(profile: Profile): Double =
        weight.latest()?.weightKg ?: profile.fallbackWeightKg

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext).also { instance = it }
        }
    }
}

fun Long.toEpochDay(): Long =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

fun today(): Long = LocalDate.now().toEpochDay()

fun Long.epochDayToLocalDate(): LocalDate = LocalDate.ofEpochDay(this)
