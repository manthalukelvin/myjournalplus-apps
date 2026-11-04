package com.celmatech.myjournalplus.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-schedules daily reminders after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                ReminderScheduler.rescheduleFromPrefs(context)
            } catch (_: Exception) { }
        }
    }
}
