package com.aditya.stride.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WeightEntry): Long

    @Update suspend fun update(entry: WeightEntry)
    @Delete suspend fun delete(entry: WeightEntry)

    @Query("SELECT * FROM weight_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entries ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<WeightEntry?>

    @Query("SELECT * FROM weight_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): WeightEntry?

    /** One point per day (the last reading of that day) — this is what the chart plots. */
    @Query(
        """
        SELECT epochDay, weightKg AS value FROM weight_entries w
        WHERE timestamp = (SELECT MAX(timestamp) FROM weight_entries w2 WHERE w2.epochDay = w.epochDay)
        GROUP BY epochDay
        ORDER BY epochDay ASC
        """
    )
    fun observeDaily(): Flow<List<DailyValue>>

    @Query("SELECT * FROM weight_entries ORDER BY timestamp ASC")
    suspend fun allForExport(): List<WeightEntry>
}

@Dao
interface MealDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MealEntry): Long

    @Update suspend fun update(entry: MealEntry)
    @Delete suspend fun delete(entry: MealEntry)

    @Query("SELECT * FROM meal_entries WHERE epochDay = :day ORDER BY timestamp ASC")
    fun observeForDay(day: Long): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entries ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MealEntry>>

    @Query(
        """
        SELECT epochDay, CAST(SUM(kcal) AS REAL) AS value FROM meal_entries
        GROUP BY epochDay ORDER BY epochDay ASC
        """
    )
    fun observeDaily(): Flow<List<DailyValue>>

    @Query("SELECT COALESCE(SUM(kcal), 0) FROM meal_entries WHERE epochDay = :day")
    fun observeTotalForDay(day: Long): Flow<Int>

    @Query("SELECT DISTINCT name FROM meal_entries ORDER BY timestamp DESC LIMIT 40")
    fun observeRecentNames(): Flow<List<String>>

    @Query("SELECT * FROM meal_entries ORDER BY timestamp ASC")
    suspend fun allForExport(): List<MealEntry>
}

@Dao
interface WaterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WaterEntry): Long

    @Delete suspend fun delete(entry: WaterEntry)

    @Query("SELECT * FROM water_entries WHERE epochDay = :day ORDER BY timestamp ASC")
    fun observeForDay(day: Long): Flow<List<WaterEntry>>

    @Query(
        """
        SELECT epochDay, SUM(liters) AS value FROM water_entries
        GROUP BY epochDay ORDER BY epochDay ASC
        """
    )
    fun observeDaily(): Flow<List<DailyValue>>

    @Query("SELECT COALESCE(SUM(liters), 0.0) FROM water_entries WHERE epochDay = :day")
    fun observeTotalForDay(day: Long): Flow<Double>

    @Query("SELECT * FROM water_entries ORDER BY timestamp ASC")
    suspend fun allForExport(): List<WaterEntry>
}

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ExerciseEntry): Long

    @Update suspend fun update(entry: ExerciseEntry)
    @Delete suspend fun delete(entry: ExerciseEntry)

    @Query("SELECT * FROM exercise_entries WHERE epochDay = :day ORDER BY timestamp ASC")
    fun observeForDay(day: Long): Flow<List<ExerciseEntry>>

    @Query("SELECT * FROM exercise_entries ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExerciseEntry>>

    @Query(
        """
        SELECT epochDay, CAST(SUM(kcal) AS REAL) AS value FROM exercise_entries
        GROUP BY epochDay ORDER BY epochDay ASC
        """
    )
    fun observeDaily(): Flow<List<DailyValue>>

    @Query("SELECT DISTINCT activity FROM exercise_entries ORDER BY timestamp DESC LIMIT 30")
    fun observeRecentActivities(): Flow<List<String>>

    @Query("SELECT * FROM exercise_entries ORDER BY timestamp ASC")
    suspend fun allForExport(): List<ExerciseEntry>
}

@Dao
interface RunDao {
    @Insert suspend fun insertSession(session: RunSession): Long
    @Update suspend fun updateSession(session: RunSession)
    @Delete suspend fun deleteSession(session: RunSession)

    @Insert suspend fun insertPoints(points: List<RunPoint>)

    @Transaction
    suspend fun saveRun(session: RunSession, points: List<RunPoint>): Long {
        val id = insertSession(session)
        if (points.isNotEmpty()) insertPoints(points.map { it.copy(runId = id) })
        return id
    }

    @Query("SELECT * FROM run_sessions ORDER BY startTime DESC")
    fun observeAll(): Flow<List<RunSession>>

    @Query("SELECT * FROM run_sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<RunSession?>

    @Query("SELECT * FROM run_points WHERE runId = :id ORDER BY timestamp ASC")
    fun observePoints(id: Long): Flow<List<RunPoint>>

    @Query("SELECT * FROM run_sessions WHERE epochDay = :day ORDER BY startTime ASC")
    fun observeForDay(day: Long): Flow<List<RunSession>>

    /** Distance per day in km, for the running chart. */
    @Query(
        """
        SELECT epochDay, SUM(distanceM) / 1000.0 AS value FROM run_sessions
        GROUP BY epochDay ORDER BY epochDay ASC
        """
    )
    fun observeDailyDistanceKm(): Flow<List<DailyValue>>

    /** Calories burned running per day — combined with manual burns on the dashboard. */
    @Query(
        """
        SELECT epochDay, CAST(SUM(kcal) AS REAL) AS value FROM run_sessions
        GROUP BY epochDay ORDER BY epochDay ASC
        """
    )
    fun observeDailyKcal(): Flow<List<DailyValue>>

    @Query("SELECT * FROM run_sessions ORDER BY startTime ASC")
    suspend fun allForExport(): List<RunSession>
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long

    @Update suspend fun update(reminder: Reminder)
    @Delete suspend fun delete(reminder: Reminder)

    @Query("SELECT * FROM reminders ORDER BY hour ASC, minute ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun enabled(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun byId(id: Long): Reminder?
}
