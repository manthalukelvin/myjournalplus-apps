package com.celmatech.myjournalplus.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.celmatech.myjournalplus.MainActivity
import com.celmatech.myjournalplus.R
import com.celmatech.myjournalplus.data.local.UserPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        showNotification(context)
        // Reschedule next day
        runBlocking {
            val prefs = UserPrefs(context)
            if (prefs.reminderEnabled.first()) {
                ReminderScheduler.scheduleDaily(
                    context,
                    prefs.reminderHour.first(),
                    prefs.reminderMinute.first()
                )
            }
        }
    }

    companion object {
        fun showNotification(context: Context) {
            val channelId = "mj_journal_reminder"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Journal reminders",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Daily reminders to journal or log mood"
                    }
                )
            }

            val openEntry = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open", "edit")
            }
            val openMood = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open", "mood")
            }
            val piEntry = PendingIntent.getActivity(
                context, 1, openEntry,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val piMood = PendingIntent.getActivity(
                context, 2, openMood,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val contentPi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("Time to reflect ✨")
                .setContentText("Capture today’s thoughts or log your mood in MyJournal+")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("A few minutes of journaling can clear your mind. Tap to write an entry or log how you feel.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentPi)
                .addAction(0, "New entry", piEntry)
                .addAction(0, "Log mood", piMood)
                .setColor(0xFF7B6CFF.toInt())
                .build()

            nm.notify(1001, notif)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        runBlocking {
            val prefs = UserPrefs(context)
            if (prefs.reminderEnabled.first()) {
                ReminderScheduler.scheduleDaily(
                    context,
                    prefs.reminderHour.first(),
                    prefs.reminderMinute.first()
                )
            }
        }
    }
}
