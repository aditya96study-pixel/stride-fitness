package com.aditya.stride.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Every record carries both an exact timestamp (epoch millis) and the local calendar
 * day it belongs to (epoch day). Storing the day explicitly keeps "per day" grouping
 * correct across timezone changes and makes the history queries cheap.
 */

@Entity(tableName = "weight_entries", indices = [Index("epochDay")])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val timestamp: Long,
    val weightKg: Double,
    val note: String? = null,
)

@Entity(tableName = "meal_entries", indices = [Index("epochDay")])
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val timestamp: Long,
    val name: String,
    val kcal: Int,
    val note: String? = null,
)

@Entity(tableName = "water_entries", indices = [Index("epochDay")])
data class WaterEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val timestamp: Long,
    val liters: Double,
)

/** Manually logged burn: weight training, cycling, sport, anything not GPS tracked. */
@Entity(tableName = "exercise_entries", indices = [Index("epochDay")])
data class ExerciseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val timestamp: Long,
    val activity: String,
    val kcal: Int,
    val durationMin: Int,
    val note: String? = null,
)

/**
 * Walking, outdoor running and treadmill work all live here. They are genuinely
 * different activities — kept apart for stats and charts by [activityType] — but they
 * share every field, and one table means the per-day calorie sum counts each session
 * exactly once with no merge step to get wrong.
 */
enum class ActivityType(val label: String, val isGps: Boolean) {
    RUN("Run", true),
    WALK("Walk", true),
    TREADMILL("Treadmill", false),
}

@Entity(tableName = "run_sessions", indices = [Index("epochDay")])
data class RunSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val startTime: Long,
    val endTime: Long,
    /** Metres, from filtered GPS fixes — or typed in for a treadmill. */
    val distanceM: Double,
    /** Milliseconds excluding auto-paused time. */
    val movingTimeMs: Long,
    /** Milliseconds from start to finish including pauses. */
    val elapsedTimeMs: Long,
    val avgSpeedMps: Double,
    val maxSpeedMps: Double,
    val elevationGainM: Double,
    /** What the app computed from the ACSM equations. */
    val kcalAuto: Int,
    /** What the user wants counted; starts equal to kcalAuto and can be overridden. */
    val kcal: Int,
    val note: String? = null,
    // No @ColumnInfo(defaultValue = ...) on the two columns below, deliberately: Room only
    // compares a column's default when the entity declares one, and a quoting mismatch
    // between the declared default and the migration's SQL fails the identity check at
    // open time. Leaving it undeclared makes the check skip the comparison entirely.
    /** One of [ActivityType]. Sessions recorded before v2 read as RUN. */
    val activityType: String = ActivityType.RUN.name,
    /** Treadmill incline, in percent. Zero for GPS activities — their grade comes from altitude. */
    val inclinePercent: Double = 0.0,
)

/**
 * [RunSession.activityType] as an enum. An extension rather than a member so Room never
 * has to decide whether it is a column, and so an unknown string from a hand-edited CSV
 * reads as a run instead of throwing.
 */
val RunSession.type: ActivityType
    get() = runCatching { ActivityType.valueOf(activityType) }.getOrDefault(ActivityType.RUN)

@Entity(
    tableName = "run_points",
    foreignKeys = [
        ForeignKey(
            entity = RunSession::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("runId")],
)
data class RunPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double,
    val speedMps: Float,
    val accuracyM: Float,
    /** Cumulative distance in metres at this point. */
    val cumulativeM: Double,
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    /** One of ReminderKind.name */
    val kind: String,
    val hour: Int,
    val minute: Int,
    /** Bit 0 = Monday ... bit 6 = Sunday. 127 = every day. */
    @ColumnInfo(defaultValue = "127") val daysMask: Int = 127,
    val enabled: Boolean = true,
)

/**
 * One row per day answering "did you train?", so consistency can be tracked without
 * caring what the session was. [epochDay] is the primary key rather than the usual
 * autogenerated id: the fact is one-per-day, and making that a database guarantee is
 * what lets logging a workout's calories mark the day trained without risking a duplicate.
 *
 * Deliberately carries no calorie field — it must never enter an energy calculation.
 */
@Entity(tableName = "training_days")
data class TrainingDay(
    @PrimaryKey val epochDay: Long,
    val trained: Boolean,
    /** 0..100. Null when the day was marked trained automatically and never confirmed. */
    val percentPlanned: Int? = null,
    /** One of [TrainingSource]. */
    val source: String = TrainingSource.MANUAL.name,
    val note: String? = null,
)

enum class TrainingSource {
    /** The user answered the question themselves. */
    MANUAL,

    /** Inferred because a workout's calories were logged that day. */
    AUTO,
}

enum class ReminderKind(val title: String) {
    FOOD("Log food"),
    WATER("Log water"),
    WEIGHT("Log weight"),
    EXERCISE("Log exercise"),
    GENERAL("Reminder"),
}

/** Row shape returned by the per-day aggregate queries that feed the charts. */
data class DailyValue(
    val epochDay: Long,
    val value: Double,
)
