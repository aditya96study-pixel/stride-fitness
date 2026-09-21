package com.aditya.stride.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WeightEntry::class,
        MealEntry::class,
        WaterEntry::class,
        ExerciseEntry::class,
        RunSession::class,
        RunPoint::class,
        Reminder::class,
        TrainingDay::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun mealDao(): MealDao
    abstract fun waterDao(): WaterDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun runDao(): RunDao
    abstract fun reminderDao(): ReminderDao
    abstract fun trainingDao(): TrainingDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * Splits one "run" into walking, running and treadmill work, and adds the
         * training-consistency table.
         *
         * `ADD COLUMN ... DEFAULT 'RUN'` backfills every existing row in the same
         * statement, so sessions recorded before this release read as runs and can be
         * re-categorised afterwards from their detail screen.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE run_sessions ADD COLUMN activityType TEXT NOT NULL DEFAULT 'RUN'"
                )
                db.execSQL(
                    "ALTER TABLE run_sessions ADD COLUMN inclinePercent REAL NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_days (
                        epochDay INTEGER NOT NULL,
                        trained INTEGER NOT NULL,
                        percentPlanned INTEGER,
                        source TEXT NOT NULL DEFAULT 'MANUAL',
                        note TEXT,
                        PRIMARY KEY(epochDay)
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "stride.db",
            )
                // Explicit migrations only. A destructive fallback here would silently
                // delete months of logged data the first time the schema changed, and
                // there is no copy of it anywhere else — a crash is the better failure.
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
