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

    private fun intentFor(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.aditya.stride.REMIND"
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
