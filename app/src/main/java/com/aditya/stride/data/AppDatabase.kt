package com.aditya.stride.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WeightEntry::class,
        MealEntry::class,
        WaterEntry::class,
        ExerciseEntry::class,
        RunSession::class,
        RunPoint::class,
        Reminder::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun mealDao(): MealDao
    abstract fun waterDao(): WaterDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun runDao(): RunDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "stride.db",
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
