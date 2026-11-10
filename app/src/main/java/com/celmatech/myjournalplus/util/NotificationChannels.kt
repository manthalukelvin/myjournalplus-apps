package com.celmatech.myjournalplus.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val REMINDER = "mj_journal_reminder"
    const val ADMIN = "mj_admin"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    REMINDER,
                    "Journal reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Daily reminders to journal or log mood"
                },
                NotificationChannel(
                    ADMIN,
                    "Announcements",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        )
    }
}
