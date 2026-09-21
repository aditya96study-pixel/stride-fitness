package com.aditya.stride.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Single access point for everything stored on the device. */
class Repository(context: Context) {

    internal val db = AppDatabase.get(context)
    val weight = db.weightDao()
    val meals = db.mealDao()
    val water = db.waterDao()
    val exercise = db.exerciseDao()
    val runs = db.runDao()
    val reminders = db.reminderDao()
    val training = db.trainingDao()
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

    /**
     * Logging a workout's calories also answers "did you train today?" — but only if the
     * day has no answer yet, so it never overwrites a typed percentage or a deliberate no.
     */
    suspend fun addExercise(
        activity: String,
        kcal: Int,
        durationMin: Int,
        timestamp: Long,
        note: String? = null,
    ): Long {
        val day = timestamp.toEpochDay()
        val id = exercise.insert(
            ExerciseEntry(
                epochDay = day,
                timestamp = timestamp,
                activity = activity.ifBlank { "Workout" },
                kcal = kcal,
                durationMin = durationMin,
                note = note?.takeIf { it.isNotBlank() },
            )
        )
        training.insertIfAbsent(
            TrainingDay(
                epochDay = day,
                trained = true,
                percentPlanned = null,
                source = TrainingSource.AUTO.name,
            )
        )
        return id
    }

    /** The user's own answer to "did you train?", which always wins over the auto-mark. */
    suspend fun setTrainingDay(epochDay: Long, trained: Boolean, percentPlanned: Int?) =
        training.upsert(
            TrainingDay(
                epochDay = epochDay,
                trained = trained,
                percentPlanned = if (trained) percentPlanned?.coerceIn(0, 100) else null,
                source = TrainingSource.MANUAL.name,
            )
        )

    /**
     * Treadmill work: no GPS, so the belt supplies distance and time and the console's
     * incline supplies the grade. Stored as a session with no points, which is the same
     * shape a CSV-restored run takes.
     */
    suspend fun addTreadmillSession(
        distanceM: Double,
        minutes: Double,
        inclinePercent: Double,
        kcal: Int,
        kcalAuto: Int,
        startTime: Long,
        note: String? = null,
    ): Long {
        val elapsedMs = (minutes * 60_000.0).toLong()
        val movingSeconds = (elapsedMs / 1000.0).coerceAtLeast(1.0)
        val speed = distanceM / movingSeconds
        return runs.saveRun(
            RunSession(
                epochDay = startTime.toEpochDay(),
                startTime = startTime,
                endTime = startTime + elapsedMs,
                distanceM = distanceM,
                movingTimeMs = elapsedMs,
                elapsedTimeMs = elapsedMs,
                avgSpeedMps = speed,
                maxSpeedMps = speed,
                // Incline over the belt distance is real vertical work, so the climb
                // figure on the session stays meaningful.
                elevationGainM = distanceM * inclinePercent / 100.0,
                kcalAuto = kcalAuto,
                kcal = kcal,
                note = note?.takeIf { it.isNotBlank() },
                activityType = ActivityType.TREADMILL.name,
                inclinePercent = inclinePercent,
            ),
            points = emptyList(),
        )
    }

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
