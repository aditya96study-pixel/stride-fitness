package com.aditya.stride.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aditya.stride.data.ActivityType
import com.aditya.stride.data.DailyValue
import com.aditya.stride.data.ExerciseEntry
import com.aditya.stride.data.MealEntry
import com.aditya.stride.data.Profile
import com.aditya.stride.data.Reminder
import com.aditya.stride.data.Repository
import com.aditya.stride.data.RunPoint
import com.aditya.stride.data.RunSession
import com.aditya.stride.data.ThemeMode
import com.aditya.stride.data.TrainingDay
import com.aditya.stride.data.WaterEntry
import com.aditya.stride.data.WeightEntry
import com.aditya.stride.data.today
import com.aditya.stride.notify.ReminderScheduler
import com.aditya.stride.tracking.CalorieCalc
import com.aditya.stride.ui.components.ChartPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun List<DailyValue>.toChartPoints(): List<ChartPoint> =
    map { ChartPoint(it.epochDay, it.value) }

/** Sum two per-day series into one, keeping every day that appears in either. */
fun mergeDaily(a: List<DailyValue>, b: List<DailyValue>): List<ChartPoint> {
    val totals = linkedMapOf<Long, Double>()
    a.forEach { totals[it.epochDay] = (totals[it.epochDay] ?: 0.0) + it.value }
    b.forEach { totals[it.epochDay] = (totals[it.epochDay] ?: 0.0) + it.value }
    return totals.entries.sortedBy { it.key }.map { ChartPoint(it.key, it.value) }
}

abstract class StrideViewModel(app: Application) : AndroidViewModel(app) {
    protected val repo: Repository = Repository.get(app)

    /** Bumped when the screen resumes, so "today" is right after midnight. */
    protected val dayFlow = MutableStateFlow(today())

    fun refreshToday() {
        val now = today()
        if (dayFlow.value != now) dayFlow.value = now
    }

    protected fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val profile: StateFlow<Profile> = repo.profile.state(Profile())
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(app: Application) : StrideViewModel(app) {

    val latestWeight: StateFlow<WeightEntry?> = repo.weight.observeLatest().state(null)

    val todayKcalIn: StateFlow<Int> =
        dayFlow.flatMapLatest { repo.meals.observeTotalForDay(it) }.state(0)

    val todayWaterL: StateFlow<Double> =
        dayFlow.flatMapLatest { repo.water.observeTotalForDay(it) }.state(0.0)

    val todayRuns: StateFlow<List<RunSession>> =
        dayFlow.flatMapLatest { repo.runs.observeForDay(it) }.state(emptyList())

    val todayExercise: StateFlow<List<ExerciseEntry>> =
        dayFlow.flatMapLatest { repo.exercise.observeForDay(it) }.state(emptyList())

    val weightDaily: StateFlow<List<ChartPoint>> =
        repo.weight.observeDaily().map { it.toChartPoints() }.state(emptyList())

    val calInDaily: StateFlow<List<ChartPoint>> =
        repo.meals.observeDaily().map { it.toChartPoints() }.state(emptyList())

    /** Runs plus anything logged by hand, summed per day. */
    val calOutDaily: StateFlow<List<ChartPoint>> =
        combine(repo.runs.observeDailyKcal(), repo.exercise.observeDaily()) { runs, manual ->
            mergeDaily(runs, manual)
        }.state(emptyList())

    val waterDaily: StateFlow<List<ChartPoint>> =
        repo.water.observeDaily().map { it.toChartPoints() }.state(emptyList())

    /**
     * Merged across all three activities, which is what the Today screen shows. The
     * Trends chart deliberately does not use this — it builds one series per activity.
     */
    val distanceDaily: StateFlow<List<ChartPoint>> =
        repo.runs.observeDailyDistanceKm().map { it.toChartPoints() }.state(emptyList())

    /** Whole sessions, for the filtered per-activity chart. */
    val sessions: StateFlow<List<RunSession>> = repo.runs.observeAll().state(emptyList())
}

class WeightViewModel(app: Application) : StrideViewModel(app) {
    val entries: StateFlow<List<WeightEntry>> = repo.weight.observeAll().state(emptyList())
    val daily: StateFlow<List<ChartPoint>> =
        repo.weight.observeDaily().map { it.toChartPoints() }.state(emptyList())

    fun add(weightKg: Double, timestamp: Long, note: String?) = viewModelScope.launch {
        repo.addWeight(weightKg, timestamp, note)
    }

    fun delete(entry: WeightEntry) = viewModelScope.launch { repo.weight.delete(entry) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FoodViewModel(app: Application) : StrideViewModel(app) {
    val selectedDay = MutableStateFlow(today())

    val dayEntries: StateFlow<List<MealEntry>> =
        selectedDay.flatMapLatest { repo.meals.observeForDay(it) }.state(emptyList())

    val dayTotal: StateFlow<Int> =
        selectedDay.flatMapLatest { repo.meals.observeTotalForDay(it) }.state(0)

    val daily: StateFlow<List<ChartPoint>> =
        repo.meals.observeDaily().map { it.toChartPoints() }.state(emptyList())

    val recentNames: StateFlow<List<String>> =
        repo.meals.observeRecentNames().state(emptyList())

    val history: StateFlow<List<MealEntry>> = repo.meals.observeRecent(400).state(emptyList())

    fun shiftDay(days: Long) {
        selectedDay.value += days
    }

    fun add(name: String, kcal: Int, timestamp: Long, note: String?) = viewModelScope.launch {
        repo.addMeal(name, kcal, timestamp, note)
    }

    fun delete(entry: MealEntry) = viewModelScope.launch { repo.meals.delete(entry) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WaterViewModel(app: Application) : StrideViewModel(app) {
    val selectedDay = MutableStateFlow(today())

    val dayEntries: StateFlow<List<WaterEntry>> =
        selectedDay.flatMapLatest { repo.water.observeForDay(it) }.state(emptyList())

    val dayTotal: StateFlow<Double> =
        selectedDay.flatMapLatest { repo.water.observeTotalForDay(it) }.state(0.0)

    val daily: StateFlow<List<ChartPoint>> =
        repo.water.observeDaily().map { it.toChartPoints() }.state(emptyList())

    fun shiftDay(days: Long) {
        selectedDay.value += days
    }

    fun add(liters: Double, timestamp: Long) = viewModelScope.launch {
        repo.addWater(liters, timestamp)
    }

    fun delete(entry: WaterEntry) = viewModelScope.launch { repo.water.delete(entry) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseViewModel(app: Application) : StrideViewModel(app) {
    val selectedDay = MutableStateFlow(today())

    val dayEntries: StateFlow<List<ExerciseEntry>> =
        selectedDay.flatMapLatest { repo.exercise.observeForDay(it) }.state(emptyList())

    val daily: StateFlow<List<ChartPoint>> =
        repo.exercise.observeDaily().map { it.toChartPoints() }.state(emptyList())

    val history: StateFlow<List<ExerciseEntry>> =
        repo.exercise.observeRecent(400).state(emptyList())

    val latestWeight: StateFlow<WeightEntry?> = repo.weight.observeLatest().state(null)

    /** The "did you train?" answer for whichever day the navigator is on. */
    val trainingDay: StateFlow<TrainingDay?> =
        selectedDay.flatMapLatest { repo.training.observeDay(it) }.state(null)

    /**
     * A year of answers, which is the longest window any figure on this screen needs.
     * One query feeding three windows beats three queries.
     */
    val trainingYear: StateFlow<List<TrainingDay>> =
        dayFlow.flatMapLatest { repo.training.observeSince(it - 365) }.state(emptyList())

    fun shiftDay(days: Long) {
        selectedDay.value += days
    }

    fun add(activity: String, kcal: Int, durationMin: Int, timestamp: Long, note: String?) =
        viewModelScope.launch {
            repo.addExercise(activity, kcal, durationMin, timestamp, note)
        }

    fun delete(entry: ExerciseEntry) = viewModelScope.launch { repo.exercise.delete(entry) }

    fun setTrained(trained: Boolean, percentPlanned: Int?) = viewModelScope.launch {
        repo.setTrainingDay(selectedDay.value, trained, percentPlanned)
    }

    fun clearTrainingAnswer() = viewModelScope.launch {
        repo.training.observeDay(selectedDay.value).first()?.let { repo.training.delete(it) }
    }
}

class RunHistoryViewModel(app: Application) : StrideViewModel(app) {
    val runs: StateFlow<List<RunSession>> = repo.runs.observeAll().state(emptyList())

    val distanceDaily: StateFlow<List<ChartPoint>> =
        repo.runs.observeDailyDistanceKm().map { it.toChartPoints() }.state(emptyList())

    fun points(runId: Long): Flow<List<RunPoint>> = repo.runs.observePoints(runId)
    fun session(runId: Long): Flow<RunSession?> = repo.runs.observeSession(runId)

    fun delete(session: RunSession) = viewModelScope.launch { repo.runs.deleteSession(session) }

    fun overrideCalories(session: RunSession, kcal: Int) = viewModelScope.launch {
        repo.runs.updateSession(session.copy(kcal = kcal))
    }

    fun setNote(session: RunSession, note: String) = viewModelScope.launch {
        repo.runs.updateSession(session.copy(note = note.takeIf { it.isNotBlank() }))
    }

    /**
     * Re-categorising a past session moves it between histories and chart series. It
     * recomputes [RunSession.kcalAuto] for the new activity, but only overwrites the
     * counted figure if the user had not already overridden it.
     */
    fun setActivityType(session: RunSession, type: ActivityType) = viewModelScope.launch {
        val weightKg = repo.effectiveWeightKg(repo.profile.first())
        val auto = CalorieCalc.runKcal(
            distanceM = session.distanceM,
            movingSeconds = session.movingTimeMs / 1000.0,
            elevationGainM = session.elevationGainM,
            weightKg = weightKg,
            mode = CalorieCalc.modeFor(type),
        ).roundToInt()
        val overridden = session.kcal != session.kcalAuto
        repo.runs.updateSession(
            session.copy(
                activityType = type.name,
                kcalAuto = auto,
                kcal = if (overridden) session.kcal else auto,
            )
        )
    }
}

/**
 * Treadmill sessions are typed in rather than tracked, so this writes a session with no
 * GPS points. It shares [RunSession] with outdoor activities on purpose — the per-day
 * calorie query then counts every session exactly once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TreadmillViewModel(app: Application) : StrideViewModel(app) {
    val selectedDay = MutableStateFlow(today())

    val dayEntries: StateFlow<List<RunSession>> =
        selectedDay.flatMapLatest { repo.runs.observeForDay(it) }.state(emptyList())

    val latestWeight: StateFlow<WeightEntry?> = repo.weight.observeLatest().state(null)

    fun shiftDay(days: Long) {
        selectedDay.value += days
    }

    fun add(
        distanceKm: Double,
        minutes: Double,
        inclinePercent: Double,
        kcal: Int,
        kcalAuto: Int,
        startTime: Long,
        note: String?,
    ) = viewModelScope.launch {
        repo.addTreadmillSession(
            distanceM = distanceKm * 1000.0,
            minutes = minutes,
            inclinePercent = inclinePercent,
            kcal = kcal,
            kcalAuto = kcalAuto,
            startTime = startTime,
            note = note,
        )
    }

    fun delete(session: RunSession) = viewModelScope.launch { repo.runs.deleteSession(session) }
}

class SettingsViewModel(app: Application) : StrideViewModel(app) {
    val reminders: StateFlow<List<Reminder>> = repo.reminders.observeAll().state(emptyList())
    val latestWeight: StateFlow<WeightEntry?> = repo.weight.observeLatest().state(null)

    fun saveProfile(profile: Profile) = viewModelScope.launch {
        repo.profileStore.save(profile.copy(onboarded = true))
    }

    // One setting at a time. Going through saveProfile would rewrite every key from
    // whatever Profile the caller happened to be holding, which is how a toggle used to
    // discard text the user had typed but not yet saved.

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        repo.profileStore.setThemeMode(mode)
    }

    fun setAutoPause(value: Boolean) = viewModelScope.launch {
        repo.profileStore.setAutoPause(value)
    }

    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch {
        repo.profileStore.setKeepScreenOn(value)
    }

    fun setGpsGate(value: Float) = viewModelScope.launch {
        repo.profileStore.setGpsAccuracyGateM(value)
    }

    fun setSnoozeMinutes(value: Int) = viewModelScope.launch {
        repo.profileStore.setSnoozeMinutes(value)
    }

    fun upsertReminder(reminder: Reminder) = viewModelScope.launch {
        val id = if (reminder.id == 0L) {
            repo.reminders.insert(reminder)
        } else {
            repo.reminders.update(reminder); reminder.id
        }
        val stored = reminder.copy(id = id)
        ReminderScheduler.schedule(getApplication<Application>(), stored)
    }

    fun deleteReminder(reminder: Reminder) = viewModelScope.launch {
        ReminderScheduler.cancel(getApplication<Application>(), reminder)
        repo.reminders.delete(reminder)
    }

    fun toggleReminder(reminder: Reminder, enabled: Boolean) = viewModelScope.launch {
        val updated = reminder.copy(enabled = enabled)
        repo.reminders.update(updated)
        if (enabled) {
            ReminderScheduler.schedule(getApplication<Application>(), updated)
        } else {
            ReminderScheduler.cancel(getApplication<Application>(), updated)
        }
    }
}
