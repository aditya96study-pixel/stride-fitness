package com.aditya.stride.export

import android.content.Context
import android.net.Uri
import com.aditya.stride.BuildConfig
import com.aditya.stride.data.Profile
import com.aditya.stride.data.Repository
import com.aditya.stride.data.epochDayToLocalDate
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything the app holds, as one flat CSV. A single table with a `type` column beats
 * several files: it opens straight into a spreadsheet and filters by row type.
 *
 * The format is sparse on purpose — a weight row leaves the session columns blank — which
 * is what keeps it readable once you filter by `type` in a spreadsheet.
 *
 * GPS traces are deliberately not exported. A 45-minute run at 1 Hz is about 2,700 rows;
 * four runs a week for a year would pass half a million and destroy the one property this
 * format exists for. Statistics round-trip; route maps do not.
 */
object CsvExporter {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    val HEADER = listOf(
        "type", "date", "time", "value", "unit", "detail", "duration_min", "notes",
        "activity", "distance_km", "moving_sec", "elapsed_sec", "avg_speed_kmh",
        "max_speed_kmh", "elev_gain_m", "incline_pct", "kcal_auto", "percent_planned",
        "key", "flags",
    )

    /** The v1 header, recognised on import so old exports stay readable. */
    val HEADER_V1 = listOf(
        "type", "date", "time", "value", "unit", "detail", "duration_min", "notes",
    )

    private fun blankRow() = MutableList(HEADER.size) { "" }

    private fun MutableList<String>.set(column: String, value: String) {
        this[HEADER.indexOf(column)] = value
    }

    suspend fun build(context: Context): String {
        val repo = Repository.get(context)
        val rows = mutableListOf<List<String>>()
        rows += HEADER

        fun row(type: String, build: MutableList<String>.() -> Unit) {
            val r = blankRow()
            r.set("type", type)
            r.build()
            rows += r
        }

        // Version first, so an importer can reject a file it does not understand before
        // reading anything else.
        row("meta") { set("key", "stride_csv_version"); set("value", Csv.VERSION.toString()) }
        row("meta") { set("key", "app_version"); set("value", BuildConfig.VERSION_NAME) }
        row("meta") {
            set("key", "exported_at")
            set("value", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
        }
        row("meta") { set("key", "timezone"); set("value", ZoneId.systemDefault().id) }

        profileFields(repo.profile.first()).forEach { (key, value) ->
            row("profile") { set("key", key); set("value", value) }
        }

        repo.weight.allForExport().forEach { e ->
            row("weight") {
                set("date", e.epochDay.date())
                set("time", e.timestamp.hhmm())
                set("value", e.weightKg.dp(2))
                set("unit", "kg")
                set("notes", e.note.orEmpty())
            }
        }

        repo.meals.allForExport().forEach { e ->
            row("food") {
                set("date", e.epochDay.date())
                set("time", e.timestamp.hhmm())
                set("value", e.kcal.toString())
                set("unit", "kcal")
                set("detail", e.name)
                set("notes", e.note.orEmpty())
            }
        }

        repo.water.allForExport().forEach { e ->
            row("water") {
                set("date", e.epochDay.date())
                set("time", e.timestamp.hhmm())
                set("value", e.liters.dp(2))
                set("unit", "L")
            }
        }

        repo.exercise.allForExport().forEach { e ->
            row("exercise") {
                set("date", e.epochDay.date())
                set("time", e.timestamp.hhmm())
                set("value", e.kcal.toString())
                set("unit", "kcal")
                set("detail", e.activity)
                set("duration_min", e.durationMin.toString())
                set("notes", e.note.orEmpty())
            }
        }

        // `session`, not `run`: keeping the old type string for a row that now carries an
        // activity column is what lets the importer tell a v1 file from a v2 one.
        repo.runs.allForExport().forEach { s ->
            row("session") {
                set("date", s.epochDay.date())
                set("time", s.startTime.hhmm())
                set("value", s.kcal.toString())
                set("unit", "kcal")
                set("activity", s.activityType)
                set("distance_km", (s.distanceM / 1000.0).dp(3))
                set("moving_sec", (s.movingTimeMs / 1000).toString())
                set("elapsed_sec", (s.elapsedTimeMs / 1000).toString())
                set("avg_speed_kmh", (s.avgSpeedMps * 3.6).dp(2))
                set("max_speed_kmh", (s.maxSpeedMps * 3.6).dp(2))
                set("elev_gain_m", s.elevationGainM.dp(1))
                set("incline_pct", s.inclinePercent.dp(1))
                set("kcal_auto", s.kcalAuto.toString())
                set("notes", s.note.orEmpty())
                // Human-friendly summary, ignored on import.
                set("duration_min", (s.movingTimeMs / 60000.0).dp(1))
                set(
                    "detail",
                    "${(s.distanceM / 1000.0).dp(2)} km at " +
                        "${(s.avgSpeedMps * 3.6).dp(2)} km/h, ${s.kcal} kcal",
                )
            }
        }

        repo.training.allForExport().forEach { d ->
            row("training") {
                set("date", d.epochDay.date())
                set("value", if (d.trained) "1" else "0")
                set("percent_planned", d.percentPlanned?.toString().orEmpty())
                set("flags", d.source)
                set("notes", d.note.orEmpty())
            }
        }

        repo.reminders.allForExport().forEach { r ->
            row("reminder") {
                set("time", "%02d:%02d".format(Locale.ROOT, r.hour, r.minute))
                set("value", r.daysMask.toString())
                set("detail", r.label)
                set("activity", r.kind)
                set("flags", if (r.enabled) "enabled" else "disabled")
            }
        }

        return rows.joinToString("\n") { Csv.row(it) }
    }

    /**
     * One row per profile field, keyed by name, so a new setting extends the format
     * without a version bump — an unknown key is simply ignored on import.
     */
    fun profileFields(profile: Profile): List<Pair<String, String>> = listOf(
        "height_cm" to profile.heightCm.dp(1),
        "age" to profile.age.toString(),
        "sex" to profile.sex.name,
        "fallback_weight_kg" to profile.fallbackWeightKg.dp(1),
        "activity_level" to profile.activityLevel.name,
        "water_goal_l" to profile.waterGoalL.dp(2),
        "calorie_goal" to profile.calorieGoal.toString(),
        "auto_pause" to profile.autoPause.toString(),
        "accuracy_gate" to profile.gpsAccuracyGateM.toDouble().dp(1),
        "keep_screen_on" to profile.keepScreenOnDuringRun.toString(),
        "theme_mode" to profile.themeMode.name,
        "snooze_minutes" to profile.snoozeMinutes.toString(),
    )

    suspend fun writeTo(context: Context, uri: Uri): Boolean = runCatching {
        val csv = build(context)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(csv.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: return false
        true
    }.getOrDefault(false)

    fun suggestedFileName(): String = "stride-export-${java.time.LocalDate.now()}.csv"

    /**
     * Locale.ROOT, always. The default locale writes 72,5 for 72.5 in much of Europe and
     * India's own locales are fine but not guaranteed — either way a comma inside a number
     * gets quoted on write and read back as a different value.
     */
    private fun Double.dp(places: Int): String = String.format(Locale.ROOT, "%.${places}f", this)

    private fun Long.date(): String = epochDayToLocalDate().toString()

    private fun Long.hhmm(): String =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(timeFmt)
}
