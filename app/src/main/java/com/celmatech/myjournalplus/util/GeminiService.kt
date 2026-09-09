package com.celmatech.myjournalplus.util

import com.celmatech.myjournalplus.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Calls YOUR website API (not Firebase Functions, not Gemini directly).
 * Gemini API key stays on the server only.
 *
 * Endpoint: POST {AI_API_BASE_URL}/ai-chat
 * Header:  Authorization: Bearer <Firebase ID token>
 * Body:    { "question": "...", "chatId": "optional" }
 * Response:{ "answer": "...", "chatId": "...", "freeLimitReached": bool, "isPremium": bool }
 */
object GeminiService {

    data class AiReply(
        val answer: String,
        val chatId: String,
        val freeLimitReached: Boolean,
        val isPremium: Boolean
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = BuildConfig.AI_API_BASE_URL.trimEnd('/')

    suspend fun ask(question: String, chatId: String? = null): Result<AiReply> =
        withContext(Dispatchers.IO) {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                    ?: return@withContext Result.failure(Exception("Please sign in again to use AI."))
                val idToken = user.getIdToken(false).await().token
                    ?: return@withContext Result.failure(Exception("Could not get auth token. Sign in again."))

                val bodyJson = JSONObject().apply {
                    put("question", question.trim())
                    if (!chatId.isNullOrBlank()) put("chatId", chatId)
                }

                val req = Request.Builder()
                    .url("$baseUrl/ai-chat")
                    .addHeader("Authorization", "Bearer $idToken")
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.code == 402 || resp.code == 403 && raw.contains("FREE_LIMIT")) {
                        return@withContext Result.failure(FreeLimitException())
                    }
                    if (!resp.isSuccessful) {
                        val errMsg = try {
                            JSONObject(raw).optString("error", raw.take(200))
                        } catch (_: Exception) {
                            raw.take(200)
                        }
                        if ("FREE_LIMIT" in errMsg) {
                            return@withContext Result.failure(FreeLimitException())
                        }
                        return@withContext Result.failure(Exception(errMsg.ifBlank { "AI request failed (${resp.code})" }))
                    }
                    val json = JSONObject(raw)
                    val answer = json.optString("answer", "")
                    if (answer.isBlank()) {
                        return@withContext Result.failure(Exception("Empty response from AI"))
                    }
                    Result.success(
                        AiReply(
                            answer = answer,
                            chatId = json.optString("chatId", chatId.orEmpty()),
                            freeLimitReached = json.optBoolean("freeLimitReached", false),
                            isPremium = json.optBoolean("isPremium", false)
                        )
                    )
                }
            } catch (e: FreeLimitException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(Exception(e.message?.take(220) ?: "Network error"))
            }
        }

    suspend fun generateInsights(journalContext: String): Result<String> =
        ask("Give me supportive insights based on my recent journal entries and moods.")
            .map { it.answer }

    class FreeLimitException : Exception("FREE_LIMIT_REACHED")
}
