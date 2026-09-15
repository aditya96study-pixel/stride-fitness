package com.aditya.stride.tracking

import android.location.Location
import kotlin.math.abs

/**
 * GPS on a phone is noisy: fixes wander a few metres while you stand still, and an
 * occasional fix lands hundreds of metres away. Left alone that noise inflates
 * distance — the usual reason a phone says 5.4 km when you ran 5.0 km.
 *
 * Three stages clean it up:
 *
 *  1. Gate     — throw away fixes whose reported accuracy is worse than the limit,
 *                plus anything that implies an impossible speed.
 *  2. Smooth   — a one-dimensional Kalman filter on latitude and longitude that
 *                weights each new fix by its own accuracy.
 *  3. Deadband — only accumulate distance once the smoothed position has genuinely
 *                moved, which stops standing still from adding up.
 */
class LocationFilter(
    private val accuracyGateM: Float = 25f,
    /** Nothing on foot exceeds this; anything faster is a GPS glitch. */
    private val maxPlausibleSpeedMps: Float = 12f,
    /** Movement below this between fixes is treated as jitter. */
    private val deadbandM: Float = 2.0f,
) {

    private val kalman = KalmanLatLong(decayQMetresPerSecond = 3f)

    var lastAccepted: Location? = null
        private set

    /** Fixes rejected so far — surfaced in the UI as a signal-quality hint. */
    var rejectedCount: Int = 0
        private set

    data class Accepted(
        val location: Location,
        /** Metres added to the total by this fix (already deadbanded). */
        val deltaM: Double,
        /** Metres of climb added by this fix, 0 if altitude was unusable. */
        val deltaUpM: Double,
        val seconds: Double,
        val speedMps: Float,
    )

    fun reset() {
        kalman.reset()
        lastAccepted = null
        rejectedCount = 0
    }

    /**
     * Feed a raw fix in. Returns null when the fix was rejected or contributed
     * nothing useful, otherwise the accepted, smoothed fix and its contribution.
     */
    fun accept(raw: Location): Accepted? {
        if (!raw.hasAccuracy() || raw.accuracy <= 0f || raw.accuracy > accuracyGateM) {
            rejectedCount++
            return null
        }

        val previous = lastAccepted

        // Reject fixes that would require running faster than humanly possible.
        if (previous != null) {
            val dt = (raw.time - previous.time) / 1000.0
            if (dt > 0.2) {
                val implied = previous.distanceTo(raw) / dt
                if (implied > maxPlausibleSpeedMps) {
                    rejectedCount++
                    return null
                }
            } else if (dt <= 0.0) {
                // Out-of-order or duplicate timestamp.
                return null
            }
        }

        kalman.process(raw.latitude, raw.longitude, raw.accuracy, raw.time)

        val smoothed = Location(raw).apply {
            latitude = kalman.latitude
            longitude = kalman.longitude
            accuracy = kalman.accuracy
        }

        if (previous == null) {
            lastAccepted = smoothed
            return Accepted(smoothed, 0.0, 0.0, 0.0, speedOf(smoothed, null, 0.0))
        }

        val seconds = (smoothed.time - previous.time) / 1000.0
        val straight = previous.distanceTo(smoothed).toDouble()

        // Deadband: ignore sub-threshold wander, but keep the fix as the new anchor
        // only once it has moved, so slow genuine movement still accumulates.
        if (straight < deadbandM) return null

        val deltaUp = climbBetween(previous, smoothed)
        val speed = speedOf(smoothed, previous, seconds)

        lastAccepted = smoothed
        return Accepted(smoothed, straight, deltaUp, seconds, speed)
    }

    private fun speedOf(current: Location, previous: Location?, seconds: Double): Float {
        // The chipset's own Doppler speed is more accurate than differencing positions.
        if (current.hasSpeed() && current.speed >= 0f) {
            val plausible = current.speed <= maxPlausibleSpeedMps
            if (plausible) return current.speed
        }
        if (previous != null && seconds > 0.0) {
            val derived = (previous.distanceTo(current) / seconds).toFloat()
            if (derived <= maxPlausibleSpeedMps) return derived
        }
        return 0f
    }

    private fun climbBetween(previous: Location, current: Location): Double {
        if (!previous.hasAltitude() || !current.hasAltitude()) return 0.0
        val rise = current.altitude - previous.altitude
        // Barometric/GPS altitude jitters by a metre or two constantly; only count
        // clear gains, and never more than the horizontal distance travelled.
        if (rise <= 0.5) return 0.0
        val horizontal = previous.distanceTo(current).toDouble()
        if (abs(rise) > horizontal) return 0.0
        return rise
    }
}

/**
 * One-dimensional Kalman filter over latitude/longitude, the widely used
 * "KalmanLatLong" formulation. State is the position; measurement variance comes
 * from each fix's own accuracy, and process noise grows with elapsed time so the
 * filter loosens up when fixes are sparse.
 */
class KalmanLatLong(private val decayQMetresPerSecond: Float) {

    private var minAccuracy = 1f
    private var timeStampMs = 0L
    var latitude = 0.0; private set
    var longitude = 0.0; private set
    var accuracy = -1f; private set
    private var variance = -1f  // metres squared

    fun reset() {
        variance = -1f
        timeStampMs = 0L
        accuracy = -1f
    }

    fun process(lat: Double, lon: Double, accuracyM: Float, timeMs: Long) {
        val acc = if (accuracyM < minAccuracy) minAccuracy else accuracyM

        if (variance < 0f) {
            timeStampMs = timeMs
            latitude = lat
            longitude = lon
            variance = acc * acc
            accuracy = acc
            return
        }

        val elapsed = timeMs - timeStampMs
        if (elapsed > 0) {
            variance += elapsed * decayQMetresPerSecond * decayQMetresPerSecond / 1000f
            timeStampMs = timeMs
        }

        // Kalman gain: 0 keeps the estimate, 1 trusts the new fix completely.
        val k = variance / (variance + acc * acc)
        latitude += k * (lat - latitude)
        longitude += k * (lon - longitude)
        variance *= (1 - k)
        accuracy = kotlin.math.sqrt(variance.toDouble()).toFloat()
    }
}
