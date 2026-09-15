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
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_LABEL = "reminder_label"
        const val EXTRA_KIND = "reminder_kind"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Time to log"
        val kindName = intent.getStringExtra(EXTRA_KIND) ?: ReminderKind.GENERAL.name
        val kind = runCatching { ReminderKind.valueOf(kindName) }.getOrDefault(ReminderKind.GENERAL)

        Notifications.ensureChannels(context)
        post(context, id, label, kind)

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
            ReminderKind.EXERCISE -> "Tap to log your workout and calories burned."
            ReminderKind.GENERAL -> "Tap to open Stride."
        }

        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle(label)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(Notifications.REMINDER_ID_BASE + id.toInt(), notification)
        }
    }
}
