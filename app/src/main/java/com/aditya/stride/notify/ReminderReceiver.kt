package com.aditya.stride.notify

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aditya.stride.MainActivity
import com.aditya.stride.R
import com.aditya.stride.data.Repository
import com.aditya.stride.data.ReminderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_LABEL = "reminder_label"
        const val EXTRA_KIND = "reminder_kind"

        /** Set on the alarm a snooze arms, and on the tap that asks for one. */
        const val EXTRA_SNOOZED = "reminder_snoozed"

        const val ACTION_SNOOZE = "com.aditya.stride.SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Time to log"
        val kindName = intent.getStringExtra(EXTRA_KIND) ?: ReminderKind.GENERAL.name
        val kind = runCatching { ReminderKind.valueOf(kindName) }.getOrDefault(ReminderKind.GENERAL)

        if (intent.action == ACTION_SNOOZE) {
            handleSnoozeRequest(context, id, label, kindName)
            return
        }

        Notifications.ensureChannels(context)
        post(context, id, label, kind)

        // A snoozed fire must not re-arm: the fire it was snoozed from already queued the
        // next matching day, and arming again would walk the whole chain forward by one.
        if (intent.getBooleanExtra(EXTRA_SNOOZED, false)) return

        // Alarms are one-shot, so queue the next matching day straight away.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = Repository.get(context)
                repo.reminders.byId(id)?.let { ReminderScheduler.schedule(context, it) }
            } finally {
                pending.finish()
            }
        }
    }

    /** Dismiss what is showing and re-arm it for the user's chosen snooze length. */
    private fun handleSnoozeRequest(context: Context, id: Long, label: String, kind: String) {
        runCatching {
            NotificationManagerCompat.from(context)
                .cancel(Notifications.REMINDER_ID_BASE + id.toInt())
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val minutes = Repository.get(context).profile.first().snoozeMinutes
                ReminderScheduler.snooze(context, id, label, kind, minutes)
            } finally {
                pending.finish()
            }
        }
    }

    private fun post(context: Context, id: Long, label: String, kind: ReminderKind) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) return

        val destination = when (kind) {
            ReminderKind.FOOD -> "food"
            ReminderKind.WATER -> "water"
            ReminderKind.WEIGHT -> "weight"
            ReminderKind.EXERCISE -> "exercise"
            ReminderKind.GENERAL -> "dashboard"
        }

        val open = PendingIntent.getActivity(
            context,
            (id + 500).toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, destination)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = when (kind) {
            ReminderKind.FOOD -> "Tap to add what you ate and its calories."
            ReminderKind.WATER -> "Tap to add the water you have drunk."
            ReminderKind.WEIGHT -> "Tap to record today's weight."
            ReminderKind.EXERCISE -> "Tap to say whether you trained, and log the calories."
            ReminderKind.GENERAL -> "Tap to open Stride."
        }

        val snooze = PendingIntent.getBroadcast(
            context,
            (id + 900).toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_KIND, kind.name)
                .setData(android.net.Uri.parse("stride://snooze-request/$id")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle(label)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(R.drawable.ic_stat_run, "Snooze", snooze)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(Notifications.REMINDER_ID_BASE + id.toInt(), notification)
        }
    }
}
