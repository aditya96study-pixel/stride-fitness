package com.aditya.stride.tracking

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aditya.stride.MainActivity
import com.aditya.stride.R
import com.aditya.stride.data.Repository
import com.aditya.stride.data.RunPoint
import com.aditya.stride.data.RunSession
import com.aditya.stride.data.toEpochDay
import com.aditya.stride.notify.Notifications
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Tracks a run in a foreground service so sampling stays steady with the screen off.
 *
 * It exists only for the duration of a run: started when you tap Start, stopped and
 * fully torn down when you tap Finish or Discard. Nothing of this app runs in the
 * background at any other time.
 */
class RunTrackingService : Service() {

    companion object {
        const val ACTION_START = "com.aditya.stride.START"
        const val ACTION_PAUSE = "com.aditya.stride.PAUSE"
        const val ACTION_RESUME = "com.aditya.stride.RESUME"
        const val ACTION_FINISH = "com.aditya.stride.FINISH"
        const val ACTION_DISCARD = "com.aditya.stride.DISCARD"

        fun send(context: Context, action: String) {
            val intent = Intent(context, RunTrackingService::class.java).setAction(action)
            if (action == ACTION_START) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private lateinit var repo: Repository

    private var filter = LocationFilter()
    private var wakeLock: PowerManager.WakeLock? = null

    private var weightKg = 70.0
    private var autoPauseEnabled = true

    // Timing is done off the wall clock so a clock change mid-run cannot corrupt it.
    private var startRealtime = 0L
    private var movingAccumMs = 0L
    private var lastTickRealtime = 0L
    private var stillSinceRealtime: Long? = null
    private var nextSplitKm = 1
    private var lastSplitMovingMs = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onFix)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = Repository.get(this)
        Notifications.ensureChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> handlePause(auto = false)
            ACTION_RESUME -> handleResume(auto = false)
            ACTION_FINISH -> handleFinish(save = true)
            ACTION_DISCARD -> handleFinish(save = false)
            else -> if (!RunTracker.state.value.isActive) stopSelf()
        }
        return START_STICKY
    }

    // ---------------- lifecycle of a run ----------------

    private fun handleStart() {
        if (RunTracker.state.value.isActive) return

        ServiceCompat.startForeground(
            this,
            Notifications.RUN_NOTIFICATION_ID,
            buildNotification(RunState()),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            },
        )

        scope.launch {
            val profile = repo.profile.first()
            weightKg = repo.effectiveWeightKg(profile)
            autoPauseEnabled = profile.autoPause
            filter = LocationFilter(accuracyGateM = profile.gpsAccuracyGateM)
        }

        filter.reset()
        startRealtime = SystemClock.elapsedRealtime()
        lastTickRealtime = startRealtime
        movingAccumMs = 0L
        stillSinceRealtime = null
        nextSplitKm = 1
        lastSplitMovingMs = 0L

        RunTracker.set(
            RunState(status = RunStatus.TRACKING, startedAt = System.currentTimeMillis())
        )

        acquireWakeLock()
        requestUpdates()
        startTicker()
    }

    private fun handlePause(auto: Boolean) {
        val state = RunTracker.state.value
        if (state.status != RunStatus.TRACKING) return
        RunTracker.update { it.copy(status = RunStatus.PAUSED, autoPaused = auto, currentSpeedMps = 0f) }
        pushNotification()
    }

    private fun handleResume(auto: Boolean) {
        val state = RunTracker.state.value
        if (state.status != RunStatus.PAUSED) return
        lastTickRealtime = SystemClock.elapsedRealtime()
        stillSinceRealtime = null
        filter.reset()   // drop the stale anchor so the gap is not counted as distance
        RunTracker.update { it.copy(status = RunStatus.TRACKING, autoPaused = false) }
        pushNotification()
    }

    private fun handleFinish(save: Boolean) {
        val state = RunTracker.state.value
        if (!state.isActive) {
            stopEverything()
            return
        }
        RunTracker.update { it.copy(status = RunStatus.SAVING) }
        stopLocationUpdates()

        if (!save || state.distanceM < 10.0) {
            RunTracker.resetToIdle()
            stopEverything()
            return
        }

        scope.launch {
            val movingSeconds = state.movingTimeMs / 1000.0
            val autoKcal = state.kcal.roundToInt()
            val session = RunSession(
                epochDay = state.startedAt.toEpochDay(),
                startTime = state.startedAt,
                endTime = System.currentTimeMillis(),
                distanceM = state.distanceM,
                movingTimeMs = state.movingTimeMs,
                elapsedTimeMs = state.elapsedTimeMs,
                avgSpeedMps = if (movingSeconds > 0) state.distanceM / movingSeconds else 0.0,
                maxSpeedMps = state.maxSpeedMps.toDouble(),
                elevationGainM = state.elevationGainM,
                kcalAuto = autoKcal,
                kcal = autoKcal,
            )
            val points = state.points.map {
                RunPoint(
                    runId = 0,
                    timestamp = it.timestamp,
                    lat = it.lat,
                    lon = it.lon,
                    altitude = it.altitude,
                    speedMps = it.speedMps,
                    accuracyM = it.accuracyM,
                    cumulativeM = it.cumulativeM,
                )
            }
            val id = repo.runs.saveRun(session, points)
            RunTracker.set(RunState(savedRunId = id))
            stopEverything()
        }
    }

    private fun stopEverything() {
        stopLocationUpdates()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------- location ----------------

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestUpdates() {
        if (!hasLocationPermission()) {
            RunTracker.resetToIdle()
            stopEverything()
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdateDelayMillis(1000L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(true)
            .build()
        try {
            client.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (_: SecurityException) {
            RunTracker.resetToIdle()
            stopEverything()
        }
    }

    private fun stopLocationUpdates() {
        runCatching { client.removeLocationUpdates(locationCallback) }
    }

    private fun onFix(raw: Location) {
        val current = RunTracker.state.value
        if (current.status != RunStatus.TRACKING) {
            // Still feed the filter so accuracy stays fresh, but do not accumulate.
            if (current.status == RunStatus.PAUSED && raw.hasAccuracy()) {
                RunTracker.update { it.copy(gpsAccuracyM = raw.accuracy) }
            }
            return
        }

        val accepted = filter.accept(raw)
        if (accepted == null) {
            RunTracker.update {
                it.copy(
                    gpsAccuracyM = if (raw.hasAccuracy()) raw.accuracy else it.gpsAccuracyM,
                    rejectedFixes = filter.rejectedCount,
                )
            }
            return
        }

        val grade = CalorieCalc.grade(accepted.deltaM, accepted.deltaUpM)
        val addedKcal = CalorieCalc.segmentKcal(
            speedMps = accepted.speedMps.toDouble(),
            grade = grade,
            seconds = accepted.seconds,
            weightKg = weightKg,
        )

        RunTracker.update { state ->
            val distance = state.distanceM + accepted.deltaM
            val point = TrackPoint(
                lat = accepted.location.latitude,
                lon = accepted.location.longitude,
                altitude = if (accepted.location.hasAltitude()) accepted.location.altitude else 0.0,
                timestamp = accepted.location.time,
                speedMps = accepted.speedMps,
                accuracyM = accepted.location.accuracy,
                cumulativeM = distance,
            )
            val splits = maybeAddSplit(state, distance)
            state.copy(
                distanceM = distance,
                currentSpeedMps = accepted.speedMps,
                maxSpeedMps = maxOf(state.maxSpeedMps, accepted.speedMps),
                elevationGainM = state.elevationGainM + accepted.deltaUpM,
                kcal = state.kcal + addedKcal,
                points = state.points + point,
                splits = splits,
                gpsAccuracyM = accepted.location.accuracy,
                fixCount = state.fixCount + 1,
                rejectedFixes = filter.rejectedCount,
            )
        }

        stillSinceRealtime = if (accepted.speedMps < 0.6f) {
            stillSinceRealtime ?: SystemClock.elapsedRealtime()
        } else null
    }

    private fun maybeAddSplit(state: RunState, distanceM: Double): List<Split> {
        if (distanceM < nextSplitKm * 1000.0) return state.splits
        val duration = state.movingTimeMs - lastSplitMovingMs
        lastSplitMovingMs = state.movingTimeMs
        val split = Split(km = nextSplitKm, durationMs = duration)
        nextSplitKm++
        return state.splits + split
    }

    // ---------------- ticking ----------------

    private fun startTicker() {
        scope.launch {
            var sinceNotification = 0
            while (RunTracker.state.value.isActive || RunTracker.state.value.status == RunStatus.SAVING) {
                delay(1000)
                tick()
                if (++sinceNotification >= 2) {
                    sinceNotification = 0
                    pushNotification()
                }
            }
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val state = RunTracker.state.value
        if (state.status == RunStatus.TRACKING) {
            val delta = now - lastTickRealtime
            movingAccumMs += delta

            // Auto-pause after 8 seconds of standing still.
            val still = stillSinceRealtime
            if (autoPauseEnabled && still != null && now - still > 8_000) {
                handlePause(auto = true)
            }
        } else if (state.status == RunStatus.PAUSED && state.autoPaused) {
            if (state.currentSpeedMps > 1.0f) handleResume(auto = true)
        }
        lastTickRealtime = now

        RunTracker.update {
            it.copy(
                movingTimeMs = movingAccumMs,
                elapsedTimeMs = now - startRealtime,
            )
        }
    }

    // ---------------- notification ----------------

    private fun buildNotification(state: RunState): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_DESTINATION, "run"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun action(icon: Int, title: String, act: String) = NotificationCompat.Action(
            icon,
            title,
            PendingIntent.getService(
                this,
                act.hashCode(),
                Intent(this, RunTrackingService::class.java).setAction(act),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        val km = String.format("%.2f km", state.distanceM / 1000.0)
        val time = formatDuration(state.movingTimeMs)
        val pace = state.avgPaceSecPerKm?.let { formatPace(it) } ?: "--:--"
        val paused = state.status == RunStatus.PAUSED

        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_RUN)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle(if (paused) "Run paused — $km" else "Recording run — $km")
            .setContentText("$time  ·  $pace /km  ·  ${state.kcal.roundToInt()} kcal")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setUsesChronometer(false)

        if (paused) {
            builder.addAction(action(R.drawable.ic_stat_run, "Resume", ACTION_RESUME))
        } else {
            builder.addAction(action(R.drawable.ic_stat_run, "Pause", ACTION_PAUSE))
        }
        builder.addAction(action(R.drawable.ic_stat_run, "Finish", ACTION_FINISH))
        return builder.build()
    }

    private fun pushNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(Notifications.RUN_NOTIFICATION_ID, buildNotification(RunTracker.state.value))
        }
    }

    // ---------------- wake lock ----------------

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "stride:run").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)   // hard ceiling; released on finish regardless
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000.0).roundToLong()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

fun formatPace(secPerKm: Double): String {
    if (secPerKm <= 0 || secPerKm.isNaN() || secPerKm.isInfinite()) return "--:--"
    val total = secPerKm.roundToLong()
    return String.format("%d:%02d", total / 60, total % 60)
}
