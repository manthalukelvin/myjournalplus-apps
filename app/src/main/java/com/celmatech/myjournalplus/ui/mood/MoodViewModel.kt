package com.celmatech.myjournalplus.ui.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.celmatech.myjournalplus.data.model.MoodLog
import com.celmatech.myjournalplus.data.repository.JournalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MoodViewModel(
    private val repo: JournalRepository = JournalRepository()
) : ViewModel() {
    private val _logs = MutableStateFlow<List<MoodLog>>(emptyList())
    val logs: StateFlow<List<MoodLog>> = _logs.asStateFlow()

    init {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                repo.moodsFlow(uid).collectLatest { _logs.value = it }
            }
        }
    }

    fun logMood(mood: String, score: Int, note: String, onDone: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            repo.logMood(uid, mood, score, note)
            onDone()
        }
    }
}
