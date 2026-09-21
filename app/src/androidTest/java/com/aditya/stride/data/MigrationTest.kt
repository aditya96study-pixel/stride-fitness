package com.aditya.stride.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The release's one unrecoverable failure mode is losing the data already on the phone,
 * so this fills a version-1 database with a row in every table, runs the real migration,
 * and checks that everything is still there afterwards.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2_keepsEveryRowAndBackfillsActivityType() {
        helper.createDatabase(dbName, 1).use { db -> seedVersion1(db) }

        // Validates the post-migration schema against the generated 2.json as well as
        // running the SQL, so a mismatched column default fails here rather than on a phone.
        val db = helper.runMigrationsAndValidate(dbName, 2, true, AppDatabase.MIGRATION_1_2)

        assertEquals(1, db.count("weight_entries"))
        assertEquals(1, db.count("meal_entries"))
        assertEquals(1, db.count("water_entries"))
        assertEquals(1, db.count("exercise_entries"))
        assertEquals(2, db.count("run_sessions"))
        assertEquals(1, db.count("run_points"))
        assertEquals(1, db.count("reminders"))
        assertEquals(0, db.count("training_days"))

        // Every pre-v2 session has to read as an outdoor run with no incline.
        assertEquals(
            2,
            db.count("run_sessions WHERE activityType = 'RUN' AND inclinePercent = 0"),
        )

        // Spot-check that values survived, not just row counts.
        db.query("SELECT weightKg FROM weight_entries").use {
            assertTrue(it.moveToFirst())
            assertEquals(74.5, it.getDouble(0), 1e-9)
        }
        db.query("SELECT distanceM, kcal FROM run_sessions ORDER BY id ASC").use {
            assertTrue(it.moveToFirst())
            assertEquals(5_120.0, it.getDouble(0), 1e-9)
            assertEquals(370, it.getInt(1))
        }

        db.close()
    }

    @Test
    fun migrate1To2_acceptsAnEmptyDatabase() {
        helper.createDatabase(dbName, 1).close()
        val db = helper.runMigrationsAndValidate(dbName, 2, true, AppDatabase.MIGRATION_1_2)
        assertEquals(0, db.count("run_sessions"))
        assertEquals(0, db.count("training_days"))
        db.close()
    }

    private fun seedVersion1(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO weight_entries (epochDay, timestamp, weightKg, note) " +
                "VALUES (20000, 1728000000000, 74.5, 'morning')"
        )
        db.execSQL(
            "INSERT INTO meal_entries (epochDay, timestamp, name, kcal, note) " +
                "VALUES (20000, 1728003600000, 'Oats', 420, NULL)"
        )
        db.execSQL(
            "INSERT INTO water_entries (epochDay, timestamp, liters) " +
                "VALUES (20000, 1728007200000, 0.5)"
        )
        db.execSQL(
            "INSERT INTO exercise_entries (epochDay, timestamp, activity, kcal, durationMin, note) " +
                "VALUES (20000, 1728010800000, 'Weight training', 260, 55, NULL)"
        )
        db.execSQL(
            "INSERT INTO run_sessions (epochDay, startTime, endTime, distanceM, movingTimeMs, " +
                "elapsedTimeMs, avgSpeedMps, maxSpeedMps, elevationGainM, kcalAuto, kcal, note) " +
                "VALUES (20000, 1728014400000, 1728016200000, 5120.0, 1740000, 1800000, " +
                "2.94, 3.6, 42.0, 370, 370, 'easy')"
        )
        db.execSQL(
            "INSERT INTO run_sessions (epochDay, startTime, endTime, distanceM, movingTimeMs, " +
                "elapsedTimeMs, avgSpeedMps, maxSpeedMps, elevationGainM, kcalAuto, kcal, note) " +
                "VALUES (20001, 1728100800000, 1728102600000, 10050.0, 3600000, 3600000, " +
                "2.79, 3.4, 60.0, 720, 700, NULL)"
        )
        db.execSQL(
            "INSERT INTO run_points (runId, timestamp, lat, lon, altitude, speedMps, accuracyM, " +
                "cumulativeM) VALUES (1, 1728014400000, 12.97, 77.59, 910.0, 2.9, 8.0, 0.0)"
        )
        db.execSQL(
            "INSERT INTO reminders (label, kind, hour, minute, daysMask, enabled) " +
                "VALUES ('Log dinner', 'FOOD', 20, 30, 127, 1)"
        )
    }

    private fun SupportSQLiteDatabase.count(from: String): Int =
        query("SELECT COUNT(*) FROM $from").use {
            it.moveToFirst()
            it.getInt(0)
        }
}
