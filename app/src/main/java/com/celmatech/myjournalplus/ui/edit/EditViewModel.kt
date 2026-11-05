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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private var lastSavedAlignments: Map<String, String> = emptyMap()
    private var currentEntryId: String = ""
    private var currentUid: String = ""

    suspend fun loadEntry(uid: String, id: String): JournalEntry? = try {
        val entry = repo.getEntry(uid, id)
        if (entry != null) {
            currentUid = uid
            currentEntryId = id
            lastSavedTitle = entry.title
            lastSavedContent = entry.content
            lastSavedAlignments = entry.alignments
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
        lastSavedAlignments = emptyMap()
        _saveState.value = SaveState.Idle
    }

    /**
     * Debounced autosave (1.2s). Only writes when content actually changed.
     */
    fun scheduleAutosave(title: String, content: String, alignments: Map<String, String> = emptyMap()) {
        if (currentUid.isBlank()) return
        if (title == lastSavedTitle && content == lastSavedContent && alignments == lastSavedAlignments) return
        if (content.isBlank() && title.isBlank()) return

        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1200)
            performSave(title, content, alignments)
        }
    }

    fun save(entry: JournalEntry, onDone: (Boolean, String?) -> Unit) {
        autosaveJob?.cancel()
        if (entry.title == lastSavedTitle &&
            entry.content == lastSavedContent &&
            entry.alignments == lastSavedAlignments
        ) {
            _saveState.value = SaveState.Saved
            onDone(true, null)
            return
        }
        viewModelScope.launch {
            val result = saveMutex.withLock { withContext(Dispatchers.IO) { repo.saveEntry(entry) } }
            result.fold(
                onSuccess = { id ->
                    if (currentEntryId.isBlank() && id.isNotBlank()) {
                        currentEntryId = id
                    }
                    lastSavedTitle = entry.title
                    lastSavedContent = entry.content
                    lastSavedAlignments = entry.alignments
                    _saveState.value = SaveState.Saved
                    onDone(true, null)
                },
                onFailure = { error ->
                    _saveState.value = SaveState.Error
                    onDone(false, error.message ?: "Save failed")
                }
            )
        }
    }

    private val saveMutex = Mutex()

    private suspend fun performSave(
        title: String,
        content: String,
        alignments: Map<String, String> = emptyMap()
    ) {
        if (currentUid.isBlank()) return
        saveMutex.withLock {
            if (title == lastSavedTitle &&
                content == lastSavedContent &&
                alignments == lastSavedAlignments
            ) return
            _saveState.value = SaveState.Saving
            val entry = JournalEntry(
                id = currentEntryId,
                uid = currentUid,
                title = title,
                content = content,
                alignments = alignments
            )
            val result = withContext(Dispatchers.IO) { repo.saveEntry(entry) }
            result.fold(
                onSuccess = { id ->
                    if (currentEntryId.isBlank() && id.isNotBlank()) currentEntryId = id
                    lastSavedTitle = title
                    lastSavedContent = content
                    lastSavedAlignments = alignments
                    _saveState.value = SaveState.Saved
                },
                onFailure = { _saveState.value = SaveState.Error }
            )
        }
    }

    /** Flush any pending autosave (e.g. on navigate away / background). */
    fun flushPending(title: String, content: String, alignments: Map<String, String> = emptyMap()) {
        autosaveJob?.cancel()
        if (title == lastSavedTitle && content == lastSavedContent && alignments == lastSavedAlignments) return
        if (content.isBlank() && title.isBlank()) return
        viewModelScope.launch {
            performSave(title, content, alignments)
        }
    }

    override fun onCleared() {
        super.onCleared()
        autosaveJob?.cancel()
    }
}
