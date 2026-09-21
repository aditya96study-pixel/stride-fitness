package com.aditya.stride.export

import com.aditya.stride.data.ActivityLevel
import com.aditya.stride.data.ActivityType
import com.aditya.stride.data.Profile
import com.aditya.stride.data.Sex
import com.aditya.stride.data.ThemeMode
import com.aditya.stride.data.TrainingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvImporterTest {

    private val base = Profile()

    private fun header() = Csv.row(CsvExporter.HEADER)

    private fun row(vararg pairs: Pair<String, String>): String {
        val cells = MutableList(CsvExporter.HEADER.size) { "" }
        pairs.forEach { (name, value) -> cells[CsvExporter.HEADER.indexOf(name)] = value }
        return Csv.row(cells)
    }

    private fun v1(vararg values: String) = Csv.row(values.toList())

    private fun versionRow() = row("type" to "meta", "key" to "stride_csv_version", "value" to "2")

    private fun ready(result: ImportResult): ImportResult.Ready {
        assertTrue("expected Ready, got $result", result is ImportResult.Ready)
        return result as ImportResult.Ready
    }

    // ---------------- rejection, before the database is touched ----------------

    @Test
    fun anUnknownHeaderIsRejected() {
        val result = CsvImporter.parse("name,amount\nfoo,1", base)
        assertTrue(result is ImportResult.Rejected)
    }

    @Test
    fun anEmptyFileIsRejected() {
        assertTrue(CsvImporter.parse("", base) is ImportResult.Rejected)
    }

    @Test
    fun aHeaderWithNoRowsIsRejected() {
        assertTrue(CsvImporter.parse(header(), base) is ImportResult.Rejected)
    }

    /** Without a version row there is no way to know how to read the file. */
    @Test
    fun aV2FileWithoutAVersionRowIsRejected() {
        val text = header() + "\n" +
            row("type" to "weight", "date" to "2026-01-01", "value" to "72.5")
        assertTrue(CsvImporter.parse(text, base) is ImportResult.Rejected)
    }

    @Test
    fun aFileFromALaterVersionIsRejected() {
        val text = header() + "\n" +
            row("type" to "meta", "key" to "stride_csv_version", "value" to "99") + "\n" +
            row("type" to "weight", "date" to "2026-01-01", "value" to "72.5")
        assertTrue(CsvImporter.parse(text, base) is ImportResult.Rejected)
    }

    @Test
    fun aMostlyBrokenFileIsRejectedRatherThanPartlyApplied() {
        val rows = buildList {
            add(header())
            add(versionRow())
            // One good row, thirty broken ones: well past both thresholds.
            add(row("type" to "weight", "date" to "2026-01-01", "value" to "72.5"))
            repeat(30) { add(row("type" to "weight", "date" to "not-a-date", "value" to "x")) }
        }
        val result = CsvImporter.parse(rows.joinToString("\n"), base)
        assertTrue("expected Rejected, got $result", result is ImportResult.Rejected)
    }

    @Test
    fun aFileWithNoReadableEntriesIsRejected() {
        val text = listOf(header(), versionRow()).joinToString("\n")
        assertTrue(CsvImporter.parse(text, base) is ImportResult.Rejected)
    }

    // ---------------- a good file ----------------

    @Test
    fun everyRowTypeRoundTripsItsValues() {
        val text = listOf(
            header(),
            versionRow(),
            row("type" to "profile", "key" to "height_cm", "value" to "178.0"),
            row("type" to "profile", "key" to "theme_mode", "value" to "DARK"),
            row("type" to "profile", "key" to "activity_level", "value" to "MODERATE"),
            row("type" to "profile", "key" to "sex", "value" to "FEMALE"),
            row("type" to "profile", "key" to "unknown_future_key", "value" to "whatever"),
            row(
                "type" to "weight", "date" to "2026-01-02", "time" to "07:30",
                "value" to "72.50", "unit" to "kg", "notes" to "morning",
            ),
            row(
                "type" to "food", "date" to "2026-01-02", "time" to "08:15",
                "value" to "420", "detail" to "Oats, milk",
            ),
            row("type" to "water", "date" to "2026-01-02", "time" to "09:00", "value" to "0.50"),
            row(
                "type" to "exercise", "date" to "2026-01-02", "time" to "18:00",
                "value" to "260", "detail" to "Weight training", "duration_min" to "55",
            ),
            row(
                "type" to "session", "date" to "2026-01-02", "time" to "06:00",
                "value" to "370", "activity" to "WALK", "distance_km" to "5.120",
                "moving_sec" to "1740", "elapsed_sec" to "1800", "avg_speed_kmh" to "10.59",
                "max_speed_kmh" to "12.96", "elev_gain_m" to "42.0", "incline_pct" to "1.5",
                "kcal_auto" to "400", "notes" to "easy",
            ),
            row(
                "type" to "training", "date" to "2026-01-02", "value" to "1",
                "percent_planned" to "75", "flags" to "MANUAL",
            ),
            row(
                "type" to "reminder", "time" to "20:30", "value" to "127",
                "detail" to "Log dinner", "activity" to "FOOD", "flags" to "enabled",
            ),
        ).joinToString("\n")

        val bundle = ready(CsvImporter.parse(text, base)).bundle

        assertEquals(72.5, bundle.weights.single().weightKg, 1e-9)
        assertEquals("morning", bundle.weights.single().note)
        assertEquals(LocalDate.of(2026, 1, 2).toEpochDay(), bundle.weights.single().epochDay)

        assertEquals("Oats, milk", bundle.meals.single().name)
        assertEquals(420, bundle.meals.single().kcal)

        assertEquals(0.5, bundle.waters.single().liters, 1e-9)

        assertEquals(55, bundle.exercises.single().durationMin)
        assertEquals("Weight training", bundle.exercises.single().activity)

        val session = bundle.sessions.single()
        assertEquals(ActivityType.WALK.name, session.activityType)
        assertEquals(5120.0, session.distanceM, 1e-6)
        assertEquals(1_740_000L, session.movingTimeMs)
        assertEquals(1_800_000L, session.elapsedTimeMs)
        assertEquals(370, session.kcal)
        assertEquals(400, session.kcalAuto)
        assertEquals(1.5, session.inclinePercent, 1e-9)
        // endTime reconstructs from elapsed seconds.
        assertEquals(session.startTime + 1_800_000L, session.endTime)

        val day = bundle.training.single()
        assertTrue(day.trained)
        assertEquals(75, day.percentPlanned)
        assertEquals(TrainingSource.MANUAL.name, day.source)

        val reminder = bundle.reminders.single()
        assertEquals(20, reminder.hour)
        assertEquals(30, reminder.minute)
        assertEquals(127, reminder.daysMask)
        assertTrue(reminder.enabled)

        val profile = bundle.profile
        assertNotNull(profile)
        assertEquals(178.0, profile!!.heightCm, 1e-9)
        assertEquals(ThemeMode.DARK, profile.themeMode)
        assertEquals(ActivityLevel.MODERATE, profile.activityLevel)
        assertEquals(Sex.FEMALE, profile.sex)
        // A setting the file omits keeps its current value rather than resetting.
        assertEquals(base.calorieGoal, profile.calorieGoal)
    }

    @Test
    fun aFewBadRowsAreReportedWithLineNumbersAndSkipped() {
        val text = listOf(
            header(),
            versionRow(),
            row("type" to "weight", "date" to "2026-01-01", "value" to "72.5"),
            row("type" to "weight", "date" to "nonsense", "value" to "72.5"),
            row("type" to "weight", "date" to "2026-01-03", "value" to "73.0"),
        ).joinToString("\n")

        val result = ready(CsvImporter.parse(text, base))
        assertEquals(2, result.bundle.weights.size)
        assertEquals(listOf(4), result.errors.map { it.line })
    }

    @Test
    fun anUnknownRowTypeIsCountedAsIgnoredRatherThanAnError() {
        val text = listOf(
            header(),
            versionRow(),
            row("type" to "weight", "date" to "2026-01-01", "value" to "72.5"),
            row("type" to "sleep", "date" to "2026-01-01", "value" to "8"),
        ).joinToString("\n")

        val result = ready(CsvImporter.parse(text, base))
        assertEquals(1, result.ignored)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun aQuotedNoteWithACommaSurvivesTheRoundTrip() {
        val text = listOf(
            header(),
            versionRow(),
            row("type" to "weight", "date" to "2026-01-01", "value" to "72.5",
                "notes" to "after a long, slow run"),
        ).joinToString("\n")
        assertEquals(
            "after a long, slow run",
            ready(CsvImporter.parse(text, base)).bundle.weights.single().note,
        )
    }

    /** A spreadsheet in a comma-decimal locale writes 72,5; reading it as 725 would be worse. */
    @Test
    fun aCommaDecimalSeparatorIsAccepted() {
        val text = listOf(
            header(),
            versionRow(),
            row("type" to "weight", "date" to "2026-01-01", "value" to "72,5"),
        ).joinToString("\n")
        assertEquals(
            72.5,
            ready(CsvImporter.parse(text, base)).bundle.weights.single().weightKg,
            1e-9,
        )
    }

    // ---------------- the v1 file ----------------

    @Test
    fun aVersion1FileIsReadableAndFlaggedAsLossy() {
        val text = listOf(
            Csv.row(CsvExporter.HEADER_V1),
            v1("weight", "2026-01-02", "07:30", "72.50", "kg", "", "", "morning"),
            v1("food", "2026-01-02", "08:15", "420", "kcal", "Oats", "", ""),
            v1("water", "2026-01-02", "09:00", "0.50", "L", "", "", ""),
            v1("exercise", "2026-01-02", "18:00", "260", "kcal", "Weights", "55", ""),
            v1(
                "run", "2026-01-02", "06:00", "5.120", "km",
                "avg 10.59 km/h, 370 kcal, +42 m", "29.0", "easy",
            ),
        ).joinToString("\n")

        val result = ready(CsvImporter.parse(text, base))
        assertTrue("v1 files are lossy and must say so", result.bundle.legacy)
        assertEquals(1, result.bundle.weights.size)
        assertEquals(1, result.bundle.meals.size)
        assertEquals(1, result.bundle.waters.size)
        assertEquals(1, result.bundle.exercises.size)

        val session = result.bundle.sessions.single()
        // Everything v1 held is recovered; the activity defaults to a run.
        assertEquals(ActivityType.RUN.name, session.activityType)
        assertEquals(5120.0, session.distanceM, 1e-6)
        assertEquals(370, session.kcal)
        assertEquals(42.0, session.elevationGainM, 1e-9)
        assertEquals(10.59 / 3.6, session.avgSpeedMps, 1e-9)
        assertEquals(29 * 60_000L, session.movingTimeMs)
    }

    /** A v1 file needs no meta row, so requiring one must not reject it. */
    @Test
    fun aVersion1FileNeedsNoVersionRow() {
        val text = Csv.row(CsvExporter.HEADER_V1) + "\n" +
            Csv.row(listOf("weight", "2026-01-02", "07:30", "72.50", "kg", "", "", ""))
        assertTrue(CsvImporter.parse(text, base) is ImportResult.Ready)
    }
}
