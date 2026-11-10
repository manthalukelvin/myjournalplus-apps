package com.celmatech.myjournalplus.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object ReminderScheduler {
    private const val REQ = 4001

    fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerMillis(hour, minute)

        val primary = pending(context, REQ)
        // Cancel old
        try { am.cancel(primary) } catch (_: Exception) {}

        // Primary: setAlarmClock is most reliable (shows in system status, survives Doze better)
        try {
            val canScheduleExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (Build.VERSION.SDK_INT >= 21 && canScheduleExact) {
                val show = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, com.celmatech.myjournalplus.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), primary)
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, primary)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, primary)
            }
        } catch (_: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, primary)
            } catch (_: Exception) {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, primary)
            }
        } catch (_: Exception) {
            try {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, primary)
            } catch (_: Exception) { }
        }

    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 2_000) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    private fun pending(context: Context, req: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.celmatech.myjournalplus.DAILY_REMINDER"
        }
        return PendingIntent.getBroadcast(
            context, req, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try { am.cancel(pending(context, REQ)) } catch (_: Exception) {}
    }

    fun rescheduleFromPrefs(context: Context) {
        kotlinx.coroutines.runBlocking {
            try {
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val prefs = com.celmatech.myjournalplus.data.local.UserPrefs(context, uid)
                if (prefs.reminderEnabled.first()) {
                    scheduleDaily(context, prefs.reminderHour.first(), prefs.reminderMinute.first())
                }
            } catch (_: Exception) { }
        }
    }

}
