package com.celmatech.myjournalplus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.celmatech.myjournalplus.data.model.JournalEntry
import com.celmatech.myjournalplus.data.repository.JournalRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(
    private val journalRepo: JournalRepository = JournalRepository()
) : ViewModel() {

    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _selectedEntry = MutableStateFlow<JournalEntry?>(null)
    val selectedEntry: StateFlow<JournalEntry?> = _selectedEntry.asStateFlow()

    private var collectJob: Job? = null
    private var authListener: AuthStateListener? = null

    init {
        val auth = FirebaseAuth.getInstance()
        // Initial
        startCollecting(auth.currentUser?.uid)

        authListener = AuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            startCollecting(uid)
        }
        auth.addAuthStateListener(authListener!!)
    }

    private fun startCollecting(uid: String?) {
        collectJob?.cancel()
        if (uid.isNullOrBlank()) {
            _entries.value = emptyList()
            _loading.value = false
            return
        }
        _loading.value = true
        collectJob = viewModelScope.launch {
            journalRepo.entriesFlow(uid)
                .catch { e ->
                    // Don't crash — show empty / keep previous
                    _loading.value = false
                }
                .collectLatest { list ->
                    _entries.value = list
                    _loading.value = false
                }
        }
    }

    fun selectEntry(entry: JournalEntry?) {
        _selectedEntry.value = entry
    }

    fun deleteEntry(entry: JournalEntry) {
        viewModelScope.launch {
            try {
                journalRepo.deleteEntry(entry.uid, entry.id)
                if (_selectedEntry.value?.id == entry.id) _selectedEntry.value = null
            } catch (_: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        collectJob?.cancel()
        authListener?.let { FirebaseAuth.getInstance().removeAuthStateListener(it) }
    }
}
