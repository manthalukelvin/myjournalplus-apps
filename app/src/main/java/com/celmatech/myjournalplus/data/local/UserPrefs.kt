package com.celmatech.myjournalplus.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("mj_prefs")

class UserPrefs(private val context: Context) {
    companion object {
        val THEME = stringPreferencesKey("theme_id")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_TIMEOUT_MIN = intPreferencesKey("pin_timeout_min") // 0=immediate, 1,5,15
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val LAST_UNLOCK = stringPreferencesKey("last_unlock_ms")
    }

    val themeId: Flow<String> = context.dataStore.data.map { it[THEME] ?: "default" }
    val pinEnabled: Flow<Boolean> = context.dataStore.data.map { it[PIN_ENABLED] ?: false }
    val pinHash: Flow<String?> = context.dataStore.data.map { it[PIN_HASH] }
    val pinTimeoutMin: Flow<Int> = context.dataStore.data.map { it[PIN_TIMEOUT_MIN] ?: 0 }
    val reminderEnabled: Flow<Boolean> = context.dataStore.data.map { it[REMINDER_ENABLED] ?: true }
    val reminderHour: Flow<Int> = context.dataStore.data.map { it[REMINDER_HOUR] ?: 18 }
    val reminderMinute: Flow<Int> = context.dataStore.data.map { it[REMINDER_MINUTE] ?: 0 }

    suspend fun setTheme(id: String) { context.dataStore.edit { it[THEME] = id } }
    suspend fun setPin(enabled: Boolean, hash: String?) {
        context.dataStore.edit {
            it[PIN_ENABLED] = enabled
            if (hash != null) it[PIN_HASH] = hash else it.remove(PIN_HASH)
        }
    }
    suspend fun setPinTimeout(min: Int) { context.dataStore.edit { it[PIN_TIMEOUT_MIN] = min } }
    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.dataStore.edit {
            it[REMINDER_ENABLED] = enabled
            it[REMINDER_HOUR] = hour
            it[REMINDER_MINUTE] = minute
        }
    }
    suspend fun markUnlocked() {
        context.dataStore.edit { it[LAST_UNLOCK] = System.currentTimeMillis().toString() }
    }
    val lastUnlockMs: Flow<Long> = context.dataStore.data.map {
        it[LAST_UNLOCK]?.toLongOrNull() ?: 0L
    }
}
