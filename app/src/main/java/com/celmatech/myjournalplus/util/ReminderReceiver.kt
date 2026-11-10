package com.celmatech.myjournalplus.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.celmatech.myjournalplus.MainActivity
import com.celmatech.myjournalplus.R
import com.celmatech.myjournalplus.data.local.UserPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        showNotification(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val prefs = UserPrefs(context, uid)
                if (prefs.reminderEnabled.first()) {
                    ReminderScheduler.scheduleDaily(
                        context,
                        prefs.reminderHour.first(),
                        prefs.reminderMinute.first()
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun showNotification(context: Context) {
            val channelId = NotificationChannels.REMINDER
            NotificationChannels.createAll(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
