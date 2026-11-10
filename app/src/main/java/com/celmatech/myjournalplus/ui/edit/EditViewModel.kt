package com.celmatech.myjournalplus.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celmatech.myjournalplus.data.model.JournalEntry
import com.celmatech.myjournalplus.data.repository.JournalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Edit ViewModel that persists entries only when the user explicitly clicks Save. */
class EditViewModel(
    private val repo: JournalRepository = JournalRepository()
) : ViewModel() {

    enum class SaveState { Idle, Saving, Saved, Error }

    private val _saveState = MutableStateFlow(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

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

    fun save(entry: JournalEntry, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            saveMutex.withLock {
                if (entry.title == lastSavedTitle &&
                    entry.content == lastSavedContent &&
                    entry.alignments == lastSavedAlignments
                ) {
                    _saveState.value = SaveState.Saved
                    onDone(true, null)
                    return@withLock
                }

                _saveState.value = SaveState.Saving
                val result = withContext(Dispatchers.IO) { repo.saveEntry(entry) }
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
    }

    private val saveMutex = Mutex()
}
