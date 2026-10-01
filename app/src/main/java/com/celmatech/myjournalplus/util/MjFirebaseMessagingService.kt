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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives admin / FCM pushes.
 *
 * Setup guidance:
 * 1. Firebase Console → Cloud Messaging
 * 2. Or send to topic "all_users" after clients subscribe
 * 3. On login, app saves FCM token under users/{uid}.fcmTokens
 * 4. Admin can send via Console "Send test message" or Admin SDK
 */
class MjFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
            .addOnFailureListener { }
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
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
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
    }
}
