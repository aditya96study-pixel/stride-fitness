package com.aditya.stride.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.aditya.stride.data.ActivityLevel
import com.aditya.stride.data.ActivityType
import com.aditya.stride.data.ExerciseEntry
import com.aditya.stride.data.MealEntry
import com.aditya.stride.data.Profile
import com.aditya.stride.data.Reminder
import com.aditya.stride.data.ReminderKind
import com.aditya.stride.data.Repository
import com.aditya.stride.data.RunSession
import com.aditya.stride.data.Sex
import com.aditya.stride.data.ThemeMode
import com.aditya.stride.data.TrainingDay
import com.aditya.stride.data.TrainingSource
import com.aditya.stride.data.WaterEntry
import com.aditya.stride.data.WeightEntry
import com.aditya.stride.notify.ReminderScheduler
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A row that could not be read. Reported with its line number, never guessed at. */
data class RowError(val line: Int, val reason: String)

/** Everything a file contained, in memory, before anything is written. */
data class ImportBundle(
    val weights: List<WeightEntry> = emptyList(),
    val meals: List<MealEntry> = emptyList(),
    val waters: List<WaterEntry> = emptyList(),
    val exercises: List<ExerciseEntry> = emptyList(),
    val sessions: List<RunSession> = emptyList(),
    val training: List<TrainingDay> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val profile: Profile? = null,
    /** True for a v1 file, whose session rows lose fields no v1 export ever held. */
    val legacy: Boolean = false,
) {
    val entryCount: Int
        get() = weights.size + meals.size + waters.size + exercises.size +
            sessions.size + training.size + reminders.size
}

sealed interface ImportResult {
    /** Parsed and plausible. Nothing has been written yet — the user still has to agree. */
    data class Ready(
        val bundle: ImportBundle,
        val errors: List<RowError>,
        val ignored: Int,
    ) : ImportResult

    /** Not written, and the database was never opened. */
    data class Rejected(val reason: String, val errors: List<RowError> = emptyList()) :
        ImportResult
}

/**
 * Reads a Stride export back in.
 *
 * Import replaces everything, so the order of operations matters more than any single
 * safeguard: the whole file is parsed and judged before the database is touched at all,
 * and a file that is mostly unreadable is refused outright rather than half-applied. A
 * bad file plus wipe-and-replace is exactly how a year of logging disappears.
 */
object CsvImporter {

    /** Above either of these, the file is more likely broken than merely imperfect. */
    private const val MAX_ERRORS = 20
    private const val MAX_ERROR_FRACTION = 0.05

    private val zone: ZoneId get() = ZoneId.systemDefault()

    // ---------------------------------------------------------------- parsing

    suspend fun read(context: Context, uri: Uri): ImportResult {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull() ?: return ImportResult.Rejected("That file could not be read.")
        return parse(text, currentProfile(context))
    }

    private suspend fun currentProfile(context: Context): Profile =
        Repository.get(context).profile.first()

    /**
     * @param base the profile in force now, so a file that omits a setting leaves it alone
     *   rather than resetting it to the app's default.
     */
    fun parse(text: String, base: Profile): ImportResult {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) return ImportResult.Rejected("That file is empty.")

        val header = rows.first().map { it.trim().lowercase() }
        val legacy = when {
            header == CsvExporter.HEADER -> false
            header == CsvExporter.HEADER_V1 -> true
            else -> return ImportResult.Rejected(
                "That does not look like a Stride export — its first line is not a header " +
                    "this version recognises."
            )
        }

        val index = header.withIndex().associate { (i, name) -> name to i }
        fun List<String>.field(name: String): String =
            index[name]?.let { getOrNull(it) }?.trim().orEmpty()

        val dataRows = rows.drop(1)
        if (dataRows.isEmpty()) return ImportResult.Rejected("That file has a header but no rows.")

        val errors = mutableListOf<RowError>()
        var ignored = 0
        var sawVersion = legacy    // a v1 file has no meta row, and needs none

        val weights = mutableListOf<WeightEntry>()
        val meals = mutableListOf<MealEntry>()
        val waters = mutableListOf<WaterEntry>()
        val exercises = mutableListOf<ExerciseEntry>()
        val sessions = mutableListOf<RunSession>()
        val training = mutableListOf<TrainingDay>()
        val reminders = mutableListOf<Reminder>()
        val profileValues = mutableMapOf<String, String>()

        dataRows.forEachIndexed { i, row ->
            val line = i + 2      // 1-based, and the header is line 1
            fun fail(reason: String) = errors.add(RowError(line, reason))

            when (row.field("type").lowercase()) {
                "" -> Unit    // a blank separator line is not an error

                "meta" -> {
                    if (row.field("key") == "stride_csv_version") {
                        val version = row.field("value").toIntOrNull()
                        if (version == null || version > Csv.VERSION) {
                            fail("unsupported file version '${row.field("value")}'")
                        } else {
                            sawVersion = true
                        }
                    }
                }

                "profile" -> {
                    val key = row.field("key")
                    if (key.isNotEmpty()) profileValues[key] = row.field("value")
                }

                "weight" -> {
                    val day = row.field("date").epochDayOrNull()
                    val kg = row.field("value").doubleOrNull()
                    if (day == null || kg == null) fail("needs a date and a weight") else {
                        weights += WeightEntry(
                            epochDay = day,
                            timestamp = timestamp(day, row.field("time")),
                            weightKg = kg,
                            note = row.field("notes").orNull(),
                        )
                    }
                }

                "food" -> {
                    val day = row.field("date").epochDayOrNull()
                    val kcal = row.field("value").intOrNull()
                    if (day == null || kcal == null) fail("needs a date and a calorie figure") else {
                        meals += MealEntry(
                            epochDay = day,
                            timestamp = timestamp(day, row.field("time")),
                            name = row.field("detail").ifBlank { "Meal" },
                            kcal = kcal,
                            note = row.field("notes").orNull(),
                        )
                    }
                }

                "water" -> {
                    val day = row.field("date").epochDayOrNull()
                    val litres = row.field("value").doubleOrNull()
                    if (day == null || litres == null) fail("needs a date and a volume") else {
                        waters += WaterEntry(
                            epochDay = day,
                            timestamp = timestamp(day, row.field("time")),
                            liters = litres,
                        )
                    }
                }

                "exercise" -> {
                    val day = row.field("date").epochDayOrNull()
                    val kcal = row.field("value").intOrNull()
                    if (day == null || kcal == null) fail("needs a date and a calorie figure") else {
                        exercises += ExerciseEntry(
                            epochDay = day,
                            timestamp = timestamp(day, row.field("time")),
                            activity = row.field("detail").ifBlank { "Workout" },
                            kcal = kcal,
                            durationMin = row.field("duration_min").doubleOrNull()?.roundToInt() ?: 0,
                            note = row.field("notes").orNull(),
                        )
                    }
                }

                "session" -> parseSession(row, ::fail)?.let { sessions += it }

                // v1 wrote runs under `run`, with the statistics squeezed into prose.
                "run" -> parseLegacyRun(row, ::fail)?.let { sessions += it }

                "training" -> {
                    val day = row.field("date").epochDayOrNull()
                    if (day == null) fail("needs a date") else {
                        training += TrainingDay(
                            epochDay = day,
                            trained = row.field("value") == "1" ||
                                row.field("value").equals("true", ignoreCase = true),
                            percentPlanned = row.field("percent_planned").intOrNull()
                                ?.coerceIn(0, 100),
                            source = row.field("flags").enumNameOrDefault(
                                TrainingSource.entries.map { it.name },
                                TrainingSource.MANUAL.name,
                            ),
                            note = row.field("notes").orNull(),
                        )
                    }
                }

                "reminder" -> {
                    val time = row.field("time").localTimeOrNull()
                    if (time == null) fail("needs a time as HH:MM") else {
                        reminders += Reminder(
                            label = row.field("detail").ifBlank { "Reminder" },
                            kind = row.field("activity").enumNameOrDefault(
                                ReminderKind.entries.map { it.name },
                                ReminderKind.GENERAL.name,
                            ),
                            hour = time.hour,
                            minute = time.minute,
                            daysMask = row.field("value").intOrNull()?.coerceIn(0, 127) ?: 127,
                            enabled = !row.field("flags").equals("disabled", ignoreCase = true),
                        )
                    }
                }

                else -> ignored++
            }
        }

        if (!sawVersion) {
            return ImportResult.Rejected(
                "That file has no version row, so there is no way to be sure how to read it.",
                errors,
            )
        }

        val bundle = ImportBundle(
            weights = weights,
            meals = meals,
            waters = waters,
            exercises = exercises,
            sessions = sessions,
            training = training,
            reminders = reminders,
            profile = profileValues.takeIf { it.isNotEmpty() }?.let { applyProfile(base, it) },
            legacy = legacy,
        )

        if (bundle.entryCount == 0) {
            return ImportResult.Rejected("No entries could be read from that file.", errors)
        }
        if (errors.size > MAX_ERRORS || errors.size > dataRows.size * MAX_ERROR_FRACTION) {
            return ImportResult.Rejected(
                "${errors.size} of ${dataRows.size} rows could not be read, so the file " +
                    "looks damaged. Nothing has been changed.",
                errors,
            )
        }
        return ImportResult.Ready(bundle, errors, ignored)
    }

    private fun parseSession(row: List<String>, fail: (String) -> Unit): RunSession? {
        val header = CsvExporter.HEADER
        fun f(name: String) = row.getOrNull(header.indexOf(name))?.trim().orEmpty()

        val day = f("date").epochDayOrNull()
        val km = f("distance_km").doubleOrNull()
        if (day == null || km == null) {
            fail("needs a date and a distance")
            return null
        }
        // Seconds are authoritative; duration_min is the human-readable copy.
        val movingSec = f("moving_sec").doubleOrNull() ?: 0.0
        val elapsedSec = f("elapsed_sec").doubleOrNull() ?: movingSec
        val start = timestamp(day, f("time"))
        val distanceM = km * 1000.0
        val kcal = f("value").intOrNull() ?: 0
        return RunSession(
            epochDay = day,
            startTime = start,
            endTime = start + (elapsedSec * 1000).roundToLong(),
            distanceM = distanceM,
            movingTimeMs = (movingSec * 1000).roundToLong(),
            elapsedTimeMs = (elapsedSec * 1000).roundToLong(),
            avgSpeedMps = f("avg_speed_kmh").doubleOrNull()?.div(3.6)
                ?: if (movingSec > 0) distanceM / movingSec else 0.0,
            maxSpeedMps = f("max_speed_kmh").doubleOrNull()?.div(3.6) ?: 0.0,
            elevationGainM = f("elev_gain_m").doubleOrNull() ?: 0.0,
            kcalAuto = f("kcal_auto").intOrNull() ?: kcal,
            kcal = kcal,
            note = f("notes").orNull(),
            activityType = f("activity").enumNameOrDefault(
                ActivityType.entries.map { it.name },
                ActivityType.RUN.name,
            ),
            inclinePercent = f("incline_pct").doubleOrNull() ?: 0.0,
        )
    }

    private val legacySpeed = Regex("""avg\s+([\d.]+)\s*km/h""", RegexOption.IGNORE_CASE)
    private val legacyKcal = Regex("""(\d+)\s*kcal""", RegexOption.IGNORE_CASE)
    private val legacyClimb = Regex("""\+\s*([\d.]+)\s*m""", RegexOption.IGNORE_CASE)

    /**
     * v1 exported a run's speed, calories and climb as one sentence in `detail`, so this
     * reads them back out with regexes. Lossy by nature — the result is labelled as such
     * rather than presented as a faithful restore.
     */
    private fun parseLegacyRun(row: List<String>, fail: (String) -> Unit): RunSession? {
        val header = CsvExporter.HEADER_V1
        fun f(name: String) = row.getOrNull(header.indexOf(name))?.trim().orEmpty()

        val day = f("date").epochDayOrNull()
        val km = f("value").doubleOrNull()
        if (day == null || km == null) {
            fail("needs a date and a distance")
            return null
        }
        val detail = f("detail")
        val minutes = f("duration_min").doubleOrNull() ?: 0.0
        val movingSec = minutes * 60.0
        val distanceM = km * 1000.0
        val kcal = legacyKcal.find(detail)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val speedKmh = legacySpeed.find(detail)?.groupValues?.get(1)?.doubleOrNull()
        val start = timestamp(day, f("time"))
        return RunSession(
            epochDay = day,
            startTime = start,
            endTime = start + (movingSec * 1000).roundToLong(),
            distanceM = distanceM,
            movingTimeMs = (movingSec * 1000).roundToLong(),
            elapsedTimeMs = (movingSec * 1000).roundToLong(),
            avgSpeedMps = speedKmh?.div(3.6) ?: if (movingSec > 0) distanceM / movingSec else 0.0,
            maxSpeedMps = 0.0,
            elevationGainM = legacyClimb.find(detail)?.groupValues?.get(1)?.doubleOrNull() ?: 0.0,
            kcalAuto = kcal,
            kcal = kcal,
            note = f("notes").orNull(),
            activityType = ActivityType.RUN.name,
        )
    }

    private fun applyProfile(base: Profile, values: Map<String, String>): Profile = base.copy(
        heightCm = values["height_cm"]?.doubleOrNull() ?: base.heightCm,
        age = values["age"]?.intOrNull() ?: base.age,
        sex = values["sex"].enumOrDefault(Sex.entries, base.sex),
        fallbackWeightKg = values["fallback_weight_kg"]?.doubleOrNull() ?: base.fallbackWeightKg,
        activityLevel = values["activity_level"].enumOrDefault(ActivityLevel.entries, base.activityLevel),
        waterGoalL = values["water_goal_l"]?.doubleOrNull() ?: base.waterGoalL,
        calorieGoal = values["calorie_goal"]?.intOrNull() ?: base.calorieGoal,
        autoPause = values["auto_pause"]?.toBooleanStrictOrNull() ?: base.autoPause,
        gpsAccuracyGateM = values["accuracy_gate"]?.doubleOrNull()?.toFloat() ?: base.gpsAccuracyGateM,
        keepScreenOnDuringRun = values["keep_screen_on"]?.toBooleanStrictOrNull()
            ?: base.keepScreenOnDuringRun,
        themeMode = values["theme_mode"].enumOrDefault(ThemeMode.entries, base.themeMode),
        snoozeMinutes = values["snooze_minutes"]?.intOrNull() ?: base.snoozeMinutes,
    )

    // ---------------------------------------------------------------- applying

    /**
     * Replaces everything.
     *
     * The order is the safeguard. Old reminders are snapshotted before the delete, because
     * their alarms are keyed on row id and once the rows are gone those PendingIntents
     * cannot be reconstructed — the alarms would leak and keep firing forever. The
     * database work then runs in one transaction, so a throw anywhere leaves the old data
     * intact and every observing Flow re-emits it. Only after that commits are the things
     * SQLite cannot hold — the profile in DataStore, and the alarms themselves — brought
     * into line. A crash between the two leaves correct data with stale settings, which is
     * recoverable; the reverse would not be.
     */
    suspend fun apply(context: Context, bundle: ImportBundle) {
        val repo = Repository.get(context)
        val oldReminders = repo.reminders.allForExport()

        repo.db.withTransaction {
            // Points before sessions: the foreign key cascades, but being explicit means
            // the order does not depend on that staying true.
            repo.runs.deleteAllPoints()
            repo.runs.deleteAll()
            repo.weight.deleteAll()
            repo.meals.deleteAll()
            repo.water.deleteAll()
            repo.exercise.deleteAll()
            repo.training.deleteAll()
            repo.reminders.deleteAll()

            bundle.weights.forEach { repo.weight.insert(it) }
            bundle.meals.forEach { repo.meals.insert(it) }
            bundle.waters.forEach { repo.water.insert(it) }
            bundle.exercises.forEach { repo.exercise.insert(it) }
            bundle.sessions.forEach { repo.runs.insertSession(it) }
            bundle.training.forEach { repo.training.upsert(it) }
            bundle.reminders.forEach { repo.reminders.insert(it) }
        }

        oldReminders.forEach { ReminderScheduler.cancel(context, it) }
        bundle.profile?.let { repo.profileStore.save(it) }
        ReminderScheduler.rescheduleAll(context)
    }

    // ---------------------------------------------------------------- helpers

    private fun String.orNull(): String? = takeIf { it.isNotBlank() }

    private fun String.doubleOrNull(): Double? =
        // Accept a comma decimal separator: a spreadsheet in a comma-decimal locale will
        // happily write one back even though the app never does.
        trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun String.intOrNull(): Int? = trim().toIntOrNull()

    /**
     * The date column, not the timestamp, decides which day a row belongs to — so
     * re-importing in a different timezone keeps the day grouping the user saw.
     */
    private fun String.epochDayOrNull(): Long? =
        runCatching { LocalDate.parse(trim()).toEpochDay() }.getOrNull()

    private fun String.localTimeOrNull(): LocalTime? =
        runCatching { LocalTime.parse(trim()) }.getOrNull()

    private fun String.enumNameOrDefault(allowed: List<String>, fallback: String): String =
        trim().uppercase().takeIf { it in allowed } ?: fallback

    private fun <E : Enum<E>> String?.enumOrDefault(entries: List<E>, fallback: E): E =
        entries.firstOrNull { it.name == this?.trim()?.uppercase() } ?: fallback

    /** Midnight when a row has no time, which is honest about not knowing. */
    private fun timestamp(epochDay: Long, hhmm: String): Long {
        val time = hhmm.localTimeOrNull() ?: LocalTime.MIDNIGHT
        return LocalDate.ofEpochDay(epochDay).atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}
