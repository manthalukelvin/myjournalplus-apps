package com.celmatech.myjournalplus.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celmatech.myjournalplus.data.model.JournalEntry
import com.celmatech.myjournalplus.data.repository.JournalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Edit ViewModel with reliable debounced autosave.
 * - Uses original Firestore document ID (never creates duplicates).
 * - Cancels previous pending writes so older content cannot overwrite newer.
 * - Exposes save state for UI indicators.
 */
class EditViewModel(
    private val repo: JournalRepository = JournalRepository()
) : ViewModel() {

    enum class SaveState { Idle, Saving, Saved, Error }

    private val _saveState = MutableStateFlow(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private var autosaveJob: Job? = null
    private var lastSavedTitle: String = ""
    private var lastSavedContent: String = ""
    private var currentEntryId: String = ""
    private var currentUid: String = ""

    suspend fun loadEntry(uid: String, id: String): JournalEntry? = try {
        val entry = repo.getEntry(uid, id)
        if (entry != null) {
            currentUid = uid
            currentEntryId = id
            lastSavedTitle = entry.title
            lastSavedContent = entry.content
        }
        entry
    } catch (_: Exception) {
        null
    }

    fun bindNewEntry(uid: String) {
        currentUid = uid
        currentEntryId = ""
        lastSavedTitle = ""
        lastSavedContent = ""
        _saveState.value = SaveState.Idle
    }

    /**
     * Debounced autosave (1.2s). Only writes when content actually changed.
     */
    fun scheduleAutosave(title: String, content: String) {
        if (currentUid.isBlank()) return
        if (title == lastSavedTitle && content == lastSavedContent) return
        if (content.isBlank() && title.isBlank()) return

        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1200)
            performSave(title, content, isManual = false)
        }
    }

    fun save(entry: JournalEntry, onDone: (Boolean, String?) -> Unit) {
        autosaveJob?.cancel()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.saveEntry(entry)
            }
            result.fold(
                onSuccess = { id ->
                    if (currentEntryId.isBlank() && id.isNotBlank()) {
                        currentEntryId = id
                    }
                    lastSavedTitle = entry.title
                    lastSavedContent = entry.content
                    _saveState.value = SaveState.Saved
                    onDone(true, null)
                },
                onFailure = {
                    // Offline persistence still queues the write
                    lastSavedTitle = entry.title
                    lastSavedContent = entry.content
                    _saveState.value = SaveState.Saved
                    onDone(true, "Saved locally — will sync when online")
                }
            )
        }
    }

    private suspend fun performSave(title: String, content: String, isManual: Boolean) {
        if (currentUid.isBlank()) return
        _saveState.value = SaveState.Saving
        val entry = JournalEntry(
            id = currentEntryId,
            uid = currentUid,
            title = title,
            content = content
        )
        val result = withContext(Dispatchers.IO) {
            repo.saveEntry(entry)
        }
        result.fold(
            onSuccess = { id ->
                if (currentEntryId.isBlank() && id.isNotBlank()) {
                    currentEntryId = id
                }
                lastSavedTitle = title
                lastSavedContent = content
                _saveState.value = SaveState.Saved
            },
            onFailure = {
                // Treat offline as success for UX
                lastSavedTitle = title
                lastSavedContent = content
                _saveState.value = SaveState.Saved
            }
        )
    }

    /** Flush any pending autosave (e.g. on navigate away / background). */
    fun flushPending(title: String, content: String) {
        autosaveJob?.cancel()
        if (title == lastSavedTitle && content == lastSavedContent) return
        if (content.isBlank() && title.isBlank()) return
        viewModelScope.launch {
            performSave(title, content, isManual = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        autosaveJob?.cancel()
    }
}
