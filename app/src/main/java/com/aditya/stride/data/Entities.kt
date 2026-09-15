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

@Entity(tableName = "run_sessions", indices = [Index("epochDay")])
data class RunSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val startTime: Long,
    val endTime: Long,
    /** Metres, from filtered GPS fixes. */
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
)

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
