package com.celmatech.myjournalplus.util

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Central Crashlytics helper.
 * Never logs journal body, PINs, tokens, or passwords.
 */
object CrashReporting {

    private val crashlytics: FirebaseCrashlytics
        get() = FirebaseCrashlytics.getInstance()

    fun setUser(uid: String?) {
        try {
            if (uid.isNullOrBlank()) {
                crashlytics.setUserId("")
                crashlytics.setCustomKey("signed_in", false)
            } else {
                // Non-sensitive identifier only
                crashlytics.setUserId(uid)
                crashlytics.setCustomKey("signed_in", true)
            }
        } catch (_: Exception) { }
    }

    fun setPremium(isPremium: Boolean) {
        try {
            crashlytics.setCustomKey("is_premium", isPremium)
        } catch (_: Exception) { }
    }

    fun log(message: String) {
        try {
            crashlytics.log(message.take(500))
        } catch (_: Exception) { }
    }

    /**
     * Record a non-fatal exception with optional context keys.
     * @param keys must not contain journal content, PIN, or tokens
     */
    fun record(
        throwable: Throwable,
        message: String? = null,
        keys: Map<String, String> = emptyMap()
    ) {
        try {
            message?.let { crashlytics.log(it.take(500)) }
            keys.forEach { (k, v) ->
                crashlytics.setCustomKey(k.take(64), v.take(128))
            }
            crashlytics.recordException(throwable)
        } catch (_: Exception) { }
    }

    fun journalSaveFailed(e: Throwable) =
        record(e, "journal_save_failed", mapOf("area" to "journal_save"))

    fun journalDeleteFailed(e: Throwable) =
        record(e, "journal_delete_failed", mapOf("area" to "journal_delete"))

    fun moodSaveFailed(e: Throwable) =
        record(e, "mood_save_failed", mapOf("area" to "mood_save"))

    fun moodDeleteFailed(e: Throwable) =
        record(e, "mood_delete_failed", mapOf("area" to "mood_delete"))

    fun aiFailed(e: Throwable, code: String? = null) =
        record(
            e,
            "ai_failed",
            mapOf("area" to "ai", "code" to (code ?: "unknown"))
        )

    fun authFailed(e: Throwable, action: String) =
        record(e, "auth_failed", mapOf("area" to "auth", "action" to action))

    fun notificationFailed(e: Throwable) =
        record(e, "fcm_failed", mapOf("area" to "fcm"))

    fun editorException(e: Throwable) =
        record(e, "editor_exception", mapOf("area" to "editor"))
}
