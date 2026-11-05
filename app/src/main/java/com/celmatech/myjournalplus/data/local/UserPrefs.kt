package com.celmatech.myjournalplus.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private val Context.dataStore by preferencesDataStore("mj_prefs")

class UserPrefs(
    private val context: Context,
    private val accountUid: String = ""
) {
    private val cloudSettings = if (accountUid.isBlank()) null else
        FirebaseFirestore.getInstance().collection("users").document(accountUid)
            .collection("settings").document("preferences")
    companion object {
        val THEME = stringPreferencesKey("theme_id")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_TIMEOUT_MIN = intPreferencesKey("pin_timeout_min") // 0=immediate, 1,5,15
        val PIN_TRIAL_START_MS = longPreferencesKey("pin_trial_start_ms")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val LAST_UNLOCK = stringPreferencesKey("last_unlock_ms")

        /** Free PIN protection window: 14 days from first enable. */
        const val PIN_TRIAL_DAYS = 14L
        const val PIN_TRIAL_MS = PIN_TRIAL_DAYS * 24 * 60 * 60 * 1000
    }

    private fun scoped(name: String) = stringPreferencesKey(
        if (accountUid.isBlank()) name else "account_${accountUid}_$name"
    )
    private fun scopedBool(name: String) = booleanPreferencesKey(
        if (accountUid.isBlank()) name else "account_${accountUid}_$name"
    )
    private fun scopedInt(name: String) = intPreferencesKey(
        if (accountUid.isBlank()) name else "account_${accountUid}_$name"
    )
    private fun scopedLong(name: String) = longPreferencesKey(
        if (accountUid.isBlank()) name else "account_${accountUid}_$name"
    )

    val themeId: Flow<String> = context.dataStore.data.map { it[scoped("theme_id")] ?: it[THEME] ?: "default" }
    val pinEnabled: Flow<Boolean> = context.dataStore.data.map { it[scopedBool("pin_enabled")] ?: it[PIN_ENABLED] ?: false }
    val pinHash: Flow<String?> = context.dataStore.data.map { it[scoped("pin_hash")] ?: it[PIN_HASH] }
    val pinTimeoutMin: Flow<Int> = context.dataStore.data.map { it[scopedInt("pin_timeout_min")] ?: it[PIN_TIMEOUT_MIN] ?: 0 }
    val pinTrialStartMs: Flow<Long> = context.dataStore.data.map { it[scopedLong("pin_trial_start_ms")] ?: it[PIN_TRIAL_START_MS] ?: 0L }
    val reminderEnabled: Flow<Boolean> = context.dataStore.data.map { it[scopedBool("reminder_enabled")] ?: it[REMINDER_ENABLED] ?: true }
    val reminderHour: Flow<Int> = context.dataStore.data.map { it[scopedInt("reminder_hour")] ?: it[REMINDER_HOUR] ?: 18 }
    val reminderMinute: Flow<Int> = context.dataStore.data.map { it[scopedInt("reminder_minute")] ?: it[REMINDER_MINUTE] ?: 0 }

    /**
     * PIN is available for free during the 14-day trial (from first enable).
     * After that, only premium users may keep / enable PIN protection.
     */
    fun isPinAllowed(isPremium: Boolean, trialStartMs: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (isPremium) return true
        if (trialStartMs <= 0L) return true // not started yet → still free to enable
        return (now - trialStartMs) < PIN_TRIAL_MS
    }

    fun pinTrialDaysLeft(trialStartMs: Long, now: Long = System.currentTimeMillis()): Int {
        if (trialStartMs <= 0L) return PIN_TRIAL_DAYS.toInt()
        val remaining = PIN_TRIAL_MS - (now - trialStartMs)
        return (remaining / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
    }

    suspend fun setTheme(id: String) {
        context.dataStore.edit { it[scoped("theme_id")] = id }
        persistCloud(mapOf("theme" to id))
    }

    suspend fun setPin(enabled: Boolean, hash: String?) {
        context.dataStore.edit {
            it[scopedBool("pin_enabled")] = enabled
            if (hash != null) it[scoped("pin_hash")] = hash else it.remove(scoped("pin_hash"))
            // Start trial clock on first enable
            if (enabled && it[scopedLong("pin_trial_start_ms")] == null) {
                it[scopedLong("pin_trial_start_ms")] = System.currentTimeMillis()
            }
        }
        persistCloud(mapOf("pinEnabled" to enabled, "pinHash" to hash))
    }

    suspend fun setPinTimeout(min: Int) {
        context.dataStore.edit { it[scopedInt("pin_timeout_min")] = min }
        persistCloud(mapOf("pinTimeoutMin" to min))
    }

    suspend fun setPinHash(hash: String) {
        context.dataStore.edit { it[scoped("pin_hash")] = hash }
        persistCloud(mapOf("pinHash" to hash))
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit {
            it[scopedBool("reminder_enabled")] = enabled
            it[scopedInt("reminder_hour")] = hour
            it[scopedInt("reminder_minute")] = minute
        }
        persistCloud(
            mapOf(
                "reminderEnabled" to enabled,
                "reminderHour" to hour,
                "reminderMinute" to minute
            )
        )
    }

    suspend fun markUnlocked() {
        context.dataStore.edit { it[scoped("last_unlock_ms")] = System.currentTimeMillis().toString() }
    }

    val lastUnlockMs: Flow<Long> = context.dataStore.data.map {
        (it[scoped("last_unlock_ms")] ?: it[LAST_UNLOCK])?.toLongOrNull() ?: 0L
    }

    suspend fun syncFromCloud() {
        val snap = cloudSettings?.get()?.await() ?: return
        val data = snap.data ?: return
        context.dataStore.edit { prefs ->
            (data["theme"] as? String)?.let { prefs[scoped("theme_id")] = it }
            (data["pinEnabled"] as? Boolean)?.let { prefs[scopedBool("pin_enabled")] = it }
            (data["pinHash"] as? String)?.let { prefs[scoped("pin_hash")] = it }
            (data["pinTimeoutMin"] as? Number)?.let { prefs[scopedInt("pin_timeout_min")] = it.toInt() }
            (data["reminderEnabled"] as? Boolean)?.let { prefs[scopedBool("reminder_enabled")] = it }
            (data["reminderHour"] as? Number)?.let { prefs[scopedInt("reminder_hour")] = it.toInt() }
            (data["reminderMinute"] as? Number)?.let { prefs[scopedInt("reminder_minute")] = it.toInt() }
        }
    }

    private suspend fun persistCloud(values: Map<String, Any?>) {
        try {
            cloudSettings?.set(
                values + ("updatedAt" to com.google.firebase.Timestamp.now()),
                com.google.firebase.firestore.SetOptions.merge()
            )?.await()
        } catch (_: Exception) {
            // Local DataStore remains authoritative while offline.
        }
    }
}
