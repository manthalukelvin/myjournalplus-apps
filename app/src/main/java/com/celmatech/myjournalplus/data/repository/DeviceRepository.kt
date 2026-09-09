package com.celmatech.myjournalplus.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class DeviceSession(
    val deviceId: String = "",
    val name: String = "",
    val platform: String = "Android",
    val lastActive: Timestamp? = null,
    val approxLocation: String = "Unknown",
    val isCurrent: Boolean = false,
    val lastActiveLabel: String = ""
)

class DeviceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun ref(uid: String) = db.collection("users").document(uid).collection("devices")

    fun currentDeviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    suspend fun registerOrTouch(context: Context, uid: String) {
        val id = currentDeviceId(context)
        val name = "${Build.MANUFACTURER} ${Build.MODEL}".replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        val locale = Locale.getDefault()
        val region = listOfNotNull(
            locale.displayCountry.takeIf { it.isNotBlank() },
            locale.displayLanguage.takeIf { it.isNotBlank() },
            TimeZone.getDefault().id
        ).joinToString(" · ").ifBlank { "Unknown" }

        val data = hashMapOf(
            "deviceId" to id,
            "name" to name,
            "platform" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "lastActive" to Timestamp.now(),
            "approxLocation" to region,
            "appVersion" to "1.0.0"
        )
        ref(uid).document(id).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    fun devicesFlow(uid: String, currentId: String): Flow<List<DeviceSession>> = callbackFlow {
        fun mapDocs(docs: List<com.google.firebase.firestore.DocumentSnapshot>): List<DeviceSession> {
            val fmt = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
            return docs.map { d ->
                val ts = d.getTimestamp("lastActive")
                DeviceSession(
                    deviceId = d.id,
                    name = d.getString("name") ?: "Device",
                    platform = d.getString("platform") ?: "Android",
                    lastActive = ts,
                    approxLocation = d.getString("approxLocation") ?: "Unknown",
                    isCurrent = d.id == currentId,
                    lastActiveLabel = ts?.toDate()?.let { fmt.format(it) } ?: "—"
                )
            }.sortedByDescending { it.lastActive?.seconds ?: 0L }
        }

        // Prefer ordered query; fall back to plain listener if index missing
        val reg = try {
            ref(uid).orderBy("lastActive", Query.Direction.DESCENDING)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        ref(uid).addSnapshotListener { snap2, _ ->
                            trySend(mapDocs(snap2?.documents ?: emptyList()))
                        }
                        return@addSnapshotListener
                    }
                    trySend(mapDocs(snap?.documents ?: emptyList()))
                }
        } catch (_: Exception) {
            ref(uid).addSnapshotListener { snap, _ ->
                trySend(mapDocs(snap?.documents ?: emptyList()))
            }
        }
        awaitClose { reg.remove() }
    }

    suspend fun remoteLogout(uid: String, deviceId: String) {
        db.collection("users").document(uid)
            .update("forceLogoutDevices", com.google.firebase.firestore.FieldValue.arrayUnion(deviceId))
            .await()
        ref(uid).document(deviceId).delete().await()
    }
}
