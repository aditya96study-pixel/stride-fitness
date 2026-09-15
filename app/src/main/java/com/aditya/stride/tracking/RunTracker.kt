package com.aditya.stride.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RunStatus { IDLE, TRACKING, PAUSED, SAVING }

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val altitude: Double,
    val timestamp: Long,
    val speedMps: Float,
    val accuracyM: Float,
    val cumulativeM: Double,
)

data class Split(
    val km: Int,
    /** Milliseconds of moving time spent on this kilometre. */
    val durationMs: Long,
)

data class RunState(
    val status: RunStatus = RunStatus.IDLE,
    val distanceM: Double = 0.0,
    val movingTimeMs: Long = 0,
    val elapsedTimeMs: Long = 0,
    val currentSpeedMps: Float = 0f,
    val maxSpeedMps: Float = 0f,
    val elevationGainM: Double = 0.0,
    val kcal: Double = 0.0,
    val points: List<TrackPoint> = emptyList(),
    val splits: List<Split> = emptyList(),
    /** Accuracy of the most recent usable fix, metres. Null until the first fix. */
    val gpsAccuracyM: Float? = null,
    val fixCount: Int = 0,
    val rejectedFixes: Int = 0,
    val autoPaused: Boolean = false,
    val startedAt: Long = 0L,
    /** Set once a run has been written to the database. */
    val savedRunId: Long? = null,
) {
    val distanceKm: Double get() = distanceM / 1000.0

    /** Average moving speed, m/s. */
    val avgSpeedMps: Double
        get() = if (movingTimeMs > 0) distanceM / (movingTimeMs / 1000.0) else 0.0

    /** Seconds per kilometre, or null when not moving yet. */
    val avgPaceSecPerKm: Double?
        get() = if (distanceM > 20) (movingTimeMs / 1000.0) / (distanceM / 1000.0) else null

    val currentPaceSecPerKm: Double?
        get() = if (currentSpeedMps > 0.3f) 1000.0 / currentSpeedMps else null

    val isActive: Boolean get() = status == RunStatus.TRACKING || status == RunStatus.PAUSED
}

/**
 * Shared, process-wide run state. The tracking service writes it, the UI reads it.
 * Using a plain object rather than service binding keeps the screen simple and
 * means rotating the phone or leaving the screen never interrupts a run.
 */
object RunTracker {
    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    internal fun update(block: (RunState) -> RunState) {
        _state.value = block(_state.value)
    }

    internal fun set(state: RunState) {
        _state.value = state
    }

    fun clearSavedId() = update { it.copy(savedRunId = null) }

    fun resetToIdle() = set(RunState())
}
