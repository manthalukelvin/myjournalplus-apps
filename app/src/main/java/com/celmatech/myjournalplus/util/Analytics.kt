package com.celmatech.myjournalplus.util

import com.celmatech.myjournalplus.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lightweight analytics → POST /track on the backend.
 * Never sends journal text, PIN, or tokens in meta.
 */
object Analytics {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val baseUrl: String
        get() = BuildConfig.AI_API_BASE_URL.trimEnd('/')

    fun appOpen() = track("app_open")

    fun pageView(page: String) = track("page_view", page = page)

    fun journalCreated() = track("journal_create")

    fun journalEdited() = track("journal_edit")

    fun journalDeleted() = track("journal_delete")

    fun moodRecorded(moodLabel: String? = null) =
        track("mood_record", meta = moodLabel?.let { mapOf("mood" to it) })

    fun aiUsed() = track("ai_chat")

    fun premiumUpgradeInitiated() = track("premium_upgrade_initiated")

    fun notificationOpened() = track("notification_opened")

    fun feature(name: String) = track("feature", meta = mapOf("name" to name))

    fun track(
        event: String,
        page: String? = null,
        meta: Map<String, String>? = null
    ) {
        scope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                val idToken = try {
                    user?.getIdToken(false)?.await()?.token
                } catch (_: Exception) {
                    null
                }

                val body = JSONObject().apply {
                    put("event", event)
                    if (!page.isNullOrBlank()) put("page", page)
                    if (meta != null && meta.isNotEmpty()) {
                        put("meta", JSONObject(meta as Map<*, *>))
                    }
                }

                val reqBuilder = Request.Builder()
                    .url("$baseUrl/track")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")

                if (!idToken.isNullOrBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $idToken")
                }

                client.newCall(reqBuilder.build()).execute().use { /* fire-and-forget */ }
            } catch (_: Exception) {
                // Analytics must never crash the app
            }
        }
    }
}
