package com.aditya.stride.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.aditya.stride.data.Reminder
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reminders are plain alarms. Nothing of this app is resident between them —
 * the OS wakes it, it posts a notification, and it goes away again.
 */
object ReminderScheduler {

    const val ACTION_REMIND = "com.aditya.stride.REMIND"
    const val ACTION_SNOOZE_FIRE = "com.aditya.stride.SNOOZE_FIRE"

    /**
     * Snooze alarms are kept in a separate PendingIntent space from the daily ones.
     *
     * [schedule] cancels before it re-arms, and cancel matches on request code, action
     * and data — so a snooze sharing any of those with its reminder would be silently
     * cancelled the moment the daily alarm was re-armed, which happens on the very fire
     * the snooze came from.
     */
    private const val SNOOZE_REQUEST_OFFSET = 100_000

    private fun intentFor(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_ID, reminder.id)
            putExtra(ReminderReceiver.EXTRA_LABEL, reminder.label)
            putExtra(ReminderReceiver.EXTRA_KIND, reminder.kind)
            // Make the intent unique per reminder so alarms never overwrite each other.
            data = android.net.Uri.parse("stride://reminder/${reminder.id}")
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(context: Context, reminder: Reminder) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        cancel(context, reminder)
        if (!reminder.enabled || reminder.daysMask == 0) return

        val triggerAt = nextOccurrence(reminder) ?: return
        val pending = intentFor(context, reminder)

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()

        if (canExact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            // Without the exact-alarm permission the OS may delay this a few minutes.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        manager.cancel(intentFor(context, reminder))
    }

    /**
     * Push this reminder's notification back by [minutes]. The alarm it fires carries a
     * flag telling the receiver not to re-arm the daily alarm again: the original fire
     * already did that, and a second re-arm would walk the whole chain a day forward.
     */
    fun snooze(context: Context, id: Long, label: String, kind: String, minutes: Int) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE_FIRE
            putExtra(ReminderReceiver.EXTRA_ID, id)
            putExtra(ReminderReceiver.EXTRA_LABEL, label)
            putExtra(ReminderReceiver.EXTRA_KIND, kind)
            putExtra(ReminderReceiver.EXTRA_SNOOZED, true)
            data = android.net.Uri.parse("stride://snooze/$id")
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (id + SNOOZE_REQUEST_OFFSET).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = System.currentTimeMillis() + minutes.coerceIn(1, 240) * 60_000L
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
        if (canExact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    suspend fun rescheduleAll(context: Context) {
        val repo = com.aditya.stride.data.Repository.get(context)
        repo.reminders.enabled().forEach { schedule(context, it) }
    }

    /** Next date/time matching the reminder's time-of-day and selected weekdays. */
    fun nextOccurrence(reminder: Reminder, from: LocalDateTime = LocalDateTime.now()): Long? {
        if (reminder.daysMask == 0) return null
        for (offset in 0..7) {
            val candidateDate = from.toLocalDate().plusDays(offset.toLong())
            val bit = 1 shl (candidateDate.dayOfWeek.value - 1)   // Monday = bit 0
            if (reminder.daysMask and bit == 0) continue
            val candidate = candidateDate.atTime(reminder.hour, reminder.minute)
            if (candidate.isAfter(from.plusSeconds(5))) {
                return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return null
    }

    fun daysLabel(mask: Int): String = when {
        mask == 127 -> "Every day"
        mask == 0 -> "Never"
        mask == 31 -> "Weekdays"
        mask == 96 -> "Weekends"
        else -> DayOfWeek.values()
            .filter { mask and (1 shl (it.value - 1)) != 0 }
            .joinToString(" ") { it.name.take(3).lowercase().replaceFirstChar(Char::titlecase) }
    }
}
