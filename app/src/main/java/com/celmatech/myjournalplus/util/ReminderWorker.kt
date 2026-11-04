package com.celmatech.myjournalplus.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.celmatech.myjournalplus.data.local.UserPrefs
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Daily WorkManager backup so reminders still fire if AlarmManager is delayed/killed.
 * Checks once per day near the user's preferred hour.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = UserPrefs(applicationContext)
            if (!prefs.reminderEnabled.first()) return Result.success()
            val hour = prefs.reminderHour.first()
            val minute = prefs.reminderMinute.first()
            val now = Calendar.getInstance()
            // Fire notification if we're within 30 minutes after scheduled time
            val scheduled = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            val diff = now.timeInMillis - scheduled.timeInMillis
            if (diff in 0..(30 * 60 * 1000L)) {
                ReminderReceiver.showNotification(applicationContext)
            }
            // Always re-arm AlarmManager for precision
            ReminderScheduler.scheduleDaily(applicationContext, hour, minute)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "mj_daily_reminder_work"

        fun enqueue(context: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
