package com.celmatech.myjournalplus.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object ReminderScheduler {
    private const val REQ = 4001
    private const val REQ_BACKUP = 4002

    fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerMillis(hour, minute)

        val primary = pending(context, REQ)
        val backup = pending(context, REQ_BACKUP)

        // Cancel old
        try { am.cancel(primary) } catch (_: Exception) {}
        try { am.cancel(backup) } catch (_: Exception) {}

        // Primary: setAlarmClock is most reliable (shows in system status, survives Doze better)
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                val show = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, com.celmatech.myjournalplus.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), primary)
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

        // Backup alarm +1 minute in case primary is delayed
        try {
            val backupAt = triggerAt + 60_000L
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, backupAt, backup)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, backupAt, backup)
            }
        } catch (_: Exception) { }
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
        try { am.cancel(pending(context, REQ_BACKUP)) } catch (_: Exception) {}
    }
}
