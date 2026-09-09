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
import java.util.Locale

data class DeviceSession(
    val deviceId: String = "",
    val name: String = "",
    val platform: String = "Android",
    val lastActive: Timestamp? = null,
    val approxLocation: String = "Unknown",
    val isCurrent: Boolean = false
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
        val data = hashMapOf(
            "deviceId" to id,
            "name" to name,
            "platform" to "Android ${Build.VERSION.RELEASE}",
            "lastActive" to Timestamp.now(),
            "approxLocation" to (Locale.getDefault().displayCountry.ifBlank { "Unknown" })
        )
        ref(uid).document(id).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    fun devicesFlow(uid: String, currentId: String): Flow<List<DeviceSession>> = callbackFlow {
        val reg = ref(uid).orderBy("lastActive", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.map { d ->
                    DeviceSession(
                        deviceId = d.id,
                        name = d.getString("name") ?: "Device",
                        platform = d.getString("platform") ?: "Android",
                        lastActive = d.getTimestamp("lastActive"),
                        approxLocation = d.getString("approxLocation") ?: "Unknown",
                        isCurrent = d.id == currentId
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun remoteLogout(uid: String, deviceId: String) {
        // Mark for forced logout; client checks forceLogoutDevices
        db.collection("users").document(uid)
            .update("forceLogoutDevices", com.google.firebase.firestore.FieldValue.arrayUnion(deviceId))
            .await()
        ref(uid).document(deviceId).delete().await()
    }
}
