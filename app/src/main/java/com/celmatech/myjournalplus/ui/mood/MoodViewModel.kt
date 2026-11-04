package com.celmatech.myjournalplus.ui.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.celmatech.myjournalplus.data.model.MoodLog
import com.celmatech.myjournalplus.data.model.MoodType
import com.celmatech.myjournalplus.data.repository.JournalRepository
import com.celmatech.myjournalplus.util.Analytics
import com.celmatech.myjournalplus.util.CrashReporting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

data class MoodStats(
    val todayMood: MoodLog? = null,
    val weekLogs: List<MoodLog> = emptyList(),
    val monthLogs: List<MoodLog> = emptyList(),
    val weekAverage: Double = 0.0,
    val monthAverage: Double = 0.0,
    val distribution: Map<String, Int> = emptyMap(),
    val streakDays: Int = 0,
    val totalLogs: Int = 0
)

class MoodViewModel(
    private val repo: JournalRepository = JournalRepository()
) : ViewModel() {
    private val _logs = MutableStateFlow<List<MoodLog>>(emptyList())
    val logs: StateFlow<List<MoodLog>> = _logs.asStateFlow()

    private val _stats = MutableStateFlow(MoodStats())
    val stats: StateFlow<MoodStats> = _stats.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                repo.moodsFlow(uid).collectLatest { list ->
                    _logs.value = list
                    _stats.value = computeStats(list)
                }
            }
        }
    }

    fun logMood(mood: MoodType, score: Int, note: String, onDone: (Boolean) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            onDone(false)
            return
        }
        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            val result = repo.logMood(uid, mood.label, score, note.trim())
            _saving.value = false
            if (result.isFailure) {
                val err = result.exceptionOrNull()
                _error.value = err?.message ?: "Could not save mood"
                if (err != null) CrashReporting.moodSaveFailed(err)
                onDone(false)
            } else {
                Analytics.moodRecorded(mood.label)
                onDone(true)
            }
        }
    }

    fun deleteMood(logId: String, onDone: (Boolean) -> Unit = {}) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            val result = repo.deleteMood(uid, logId)
            if (result.isFailure) {
                result.exceptionOrNull()?.let { CrashReporting.moodDeleteFailed(it) }
            }
            onDone(result.isSuccess)
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun computeStats(all: List<MoodLog>): MoodStats {
        if (all.isEmpty()) return MoodStats()

        val now = Calendar.getInstance()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val startOfWeek = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        fun dateOf(log: MoodLog): Date? = log.createdAt?.toDate()

        val todayMood = all.firstOrNull { dateOf(it)?.let { d -> !d.before(startOfToday) } == true }
        val weekLogs = all.filter { dateOf(it)?.let { d -> !d.before(startOfWeek) } == true }
        val monthLogs = all.filter { dateOf(it)?.let { d -> !d.before(startOfMonth) } == true }

        val weekAvg = if (weekLogs.isEmpty()) 0.0 else weekLogs.map { it.score }.average()
        val monthAvg = if (monthLogs.isEmpty()) 0.0 else monthLogs.map { it.score }.average()

        val distribution = all.groupingBy { it.mood }.eachCount()
            .toList()
            .sortedByDescending { it.second }
            .toMap()

        // Simple consecutive-day streak (calendar days with at least one log)
        val daysWithLog = all.mapNotNull { dateOf(it) }
            .map {
                Calendar.getInstance().apply {
                    time = it
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            .toSortedSet(reverseOrder())

        var streak = 0
        if (daysWithLog.isNotEmpty()) {
            val dayMs = 24 * 60 * 60 * 1000L
            var expected = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            // Allow streak to continue if last log was yesterday or today
            if (daysWithLog.first() < expected - dayMs) {
                streak = 0
            } else {
                for (day in daysWithLog) {
                    if (day == expected || day == expected - dayMs && streak == 0) {
                        streak++
                        expected = day - dayMs
                    } else if (day == expected) {
                        streak++
                        expected -= dayMs
                    } else {
                        break
                    }
                }
            }
        }

        return MoodStats(
            todayMood = todayMood,
            weekLogs = weekLogs,
            monthLogs = monthLogs,
            weekAverage = weekAvg,
            monthAverage = monthAvg,
            distribution = distribution,
            streakDays = streak,
            totalLogs = all.size
        )
    }
}
