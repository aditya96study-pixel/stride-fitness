package com.aditya.stride.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object Notifications {
    const val CHANNEL_RUN = "run_tracking"
    const val CHANNEL_REMINDERS = "reminders"
    const val RUN_NOTIFICATION_ID = 1001
    const val REMINDER_ID_BASE = 2000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        val run = NotificationChannel(
            CHANNEL_RUN,
            "Run tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "The live stats shown while a run is being recorded."
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            "Logging reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Nudges at the times you choose to log food, water, weight or workouts."
            setShowBadge(true)
        }

        manager.createNotificationChannel(run)
        manager.createNotificationChannel(reminders)
    }
}
