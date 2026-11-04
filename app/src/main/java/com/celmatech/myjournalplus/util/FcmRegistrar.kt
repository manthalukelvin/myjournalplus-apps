package com.celmatech.myjournalplus.util

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Registers FCM token on the user document and subscribes to broadcast topic.
 *
 * - users/{uid}.fcmTokens = arrayUnion(token)
 * - Topic: all_users (admin broadcast)
 */
object FcmRegistrar {

    const val TOPIC_ALL_USERS = "all_users"

    /**
     * Call after successful login / when auth user is available.
     * Safe to call multiple times (arrayUnion + topic subscribe are idempotent).
     */
    suspend fun registerCurrentUser() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.isNullOrBlank()) return

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .set(
                    mapOf(
                        "fcmTokens" to FieldValue.arrayUnion(token),
                        "fcmTokenUpdatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()

            FirebaseMessaging.getInstance()
                .subscribeToTopic(TOPIC_ALL_USERS)
                .await()

            CrashReporting.log("fcm_registered_and_subscribed")
        } catch (e: Exception) {
            CrashReporting.notificationFailed(e)
        }
    }

    suspend fun unregisterOnLogout() {
        try {
            FirebaseMessaging.getInstance()
                .unsubscribeFromTopic(TOPIC_ALL_USERS)
                .await()
        } catch (e: Exception) {
            CrashReporting.notificationFailed(e)
        }
    }
}
