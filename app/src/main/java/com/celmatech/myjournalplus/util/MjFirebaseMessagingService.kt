package com.celmatech.myjournalplus.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.celmatech.myjournalplus.MainActivity
import com.celmatech.myjournalplus.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives admin / FCM pushes.
 * On new token: persist to fcmTokens[] and (re)subscribe to all_users.
 */
class MjFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(
                    mapOf(
                        "fcmTokens" to FieldValue.arrayUnion(token),
                        "fcmTokenUpdatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .addOnFailureListener { e -> CrashReporting.notificationFailed(e) }
        }
        FirebaseMessaging.getInstance()
            .subscribeToTopic(FcmRegistrar.TOPIC_ALL_USERS)
            .addOnFailureListener { e -> CrashReporting.notificationFailed(e) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "MyJournal+"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val channelId = "mj_admin"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Announcements", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = message.data["open"]
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!open.isNullOrBlank()) putExtra("open", open)
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        )
        Analytics.notificationOpened()
    }
}
