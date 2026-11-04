package com.celmatech.myjournalplus.util

import com.celmatech.myjournalplus.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Calls the MyJournal+ backend AI endpoint (Gemini key stays server-side).
 * Includes client-side retries for transient network / 502 / 503 errors.
 */
object GeminiService {

    data class AiReply(
        val answer: String,
        val chatId: String,
        val freeLimitReached: Boolean,
        val isPremium: Boolean
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val baseUrl: String
        get() = BuildConfig.AI_API_BASE_URL.trimEnd('/')

    suspend fun ask(question: String, chatId: String? = null): Result<AiReply> =
        withContext(Dispatchers.IO) {
            val user = FirebaseAuth.getInstance().currentUser
                ?: return@withContext Result.failure(Exception("Please sign in again to use AI."))

            val idToken = try {
                user.getIdToken(false).await().token
            } catch (e: Exception) {
                CrashReporting.aiFailed(e, "token")
                return@withContext Result.failure(Exception("Could not get auth token. Sign in again."))
            } ?: return@withContext Result.failure(Exception("Could not get auth token. Sign in again."))

            val bodyJson = JSONObject().apply {
                put("question", question.trim())
                if (!chatId.isNullOrBlank()) put("chatId", chatId)
            }
            val payload = bodyJson.toString().toRequestBody("application/json".toMediaType())

            var lastError: Exception? = null
            val maxAttempts = 3
            for (attempt in 1..maxAttempts) {
                try {
                    val req = Request.Builder()
                        .url("$baseUrl/ai-chat")
                        .addHeader("Authorization", "Bearer $idToken")
                        .addHeader("Content-Type", "application/json")
                        .post(payload)
                        .build()

                    client.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string().orEmpty()
                        val code = resp.code

                        if (code == 401) {
                            return@withContext Result.failure(
                                Exception("Session expired. Please sign in again.")
                            )
                        }
                        if (code == 403 || code == 402) {
                            val errCode = try {
                                JSONObject(raw).optString("code", "")
                            } catch (_: Exception) { "" }
                            if (errCode == "FREE_LIMIT" || raw.contains("FREE_LIMIT")) {
                                return@withContext Result.failure(FreeLimitException())
                            }
                            val msg = try {
                                JSONObject(raw).optString("error", "Access denied")
                            } catch (_: Exception) { "Access denied" }
                            return@withContext Result.failure(Exception(msg))
                        }
                        if (code == 400) {
                            val msg = try {
                                JSONObject(raw).optString("error", "Invalid request")
                            } catch (_: Exception) { "Invalid request" }
                            return@withContext Result.failure(Exception(msg))
                        }

                        if (code == 502 || code == 503 || code == 429 || code >= 500) {
                            val detail = try {
                                JSONObject(raw).optString("error", raw.take(120))
                            } catch (_: Exception) { raw.take(120) }
                            lastError = Exception(friendlyServerMessage(code, detail))
                            if (attempt < maxAttempts) {
                                delay(350L * (1L shl (attempt - 1)))
                                return@use
                            }
                            CrashReporting.aiFailed(lastError!!, "http_$code")
                            return@withContext Result.failure(lastError!!)
                        }

                        if (!resp.isSuccessful) {
                            val errMsg = try {
                                JSONObject(raw).optString("error", raw.take(200))
                            } catch (_: Exception) { raw.take(200) }
                            if ("FREE_LIMIT" in errMsg) {
                                return@withContext Result.failure(FreeLimitException())
                            }
                            return@withContext Result.failure(
                                Exception(errMsg.ifBlank { "AI request failed ($code)" })
                            )
                        }

                        val json = JSONObject(raw)
                        val answer = json.optString("answer", "")
                        if (answer.isBlank()) {
                            lastError = Exception("The AI returned an empty reply. Please try again.")
                            if (attempt < maxAttempts) {
                                delay(300L * attempt)
                                return@use
                            }
                            return@withContext Result.failure(lastError!!)
                        }
                        return@withContext Result.success(
                            AiReply(
                                answer = answer,
                                chatId = json.optString("chatId", chatId.orEmpty()),
                                freeLimitReached = json.optBoolean("freeLimitReached", false),
                                isPremium = json.optBoolean("isPremium", false)
                            )
                        )
                    }
                } catch (e: FreeLimitException) {
                    return@withContext Result.failure(e)
                } catch (e: Exception) {
                    lastError = Exception(e.message?.take(220) ?: "Network error")
                    val transient = isTransient(e)
                    if (transient && attempt < maxAttempts) {
                        delay(400L * (1L shl (attempt - 1)))
                        continue
                    }
                    CrashReporting.aiFailed(e)
                    return@withContext Result.failure(lastError!!)
                }
            }
            CrashReporting.aiFailed(lastError ?: Exception("unknown"), "retries_exhausted")
            Result.failure(
                lastError ?: Exception("AI is temporarily unavailable. Please try again.")
            )
        }

    private fun friendlyServerMessage(code: Int, detail: String): String {
        return when {
            code == 429 -> "AI is busy right now. Please wait a moment and try again."
            code == 503 -> "AI service is warming up. Please try again shortly."
            "timeout" in detail.lowercase() -> "The AI took too long. Please try a shorter question."
            "not configured" in detail.lowercase() || "GEMINI_MISSING" in detail ->
                "AI is not configured on the server yet."
            else -> detail.ifBlank { "AI is temporarily unavailable. Please try again." }
        }
    }

    private fun isTransient(e: Exception): Boolean {
        val m = (e.message ?: "").lowercase()
        return listOf(
            "timeout", "timed out", "connection", "reset", "unreachable",
            "unavailable", "502", "503", "429", "failed to connect"
        ).any { it in m }
    }

    suspend fun generateInsights(journalContext: String): Result<String> =
        ask("Give me supportive insights based on my recent journal entries and moods.")
            .map { it.answer }

    class FreeLimitException : Exception("FREE_LIMIT_REACHED")
}
