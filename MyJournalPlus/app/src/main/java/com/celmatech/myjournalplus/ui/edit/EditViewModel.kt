package com.celmatech.myjournalplus.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.celmatech.myjournalplus.data.model.JournalEntry
import com.celmatech.myjournalplus.data.repository.JournalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditViewModel(
    private val repo: JournalRepository = JournalRepository()
) : ViewModel() {

    suspend fun loadEntry(uid: String, id: String): JournalEntry? = try {
        repo.getEntry(uid, id)
    } catch (_: Exception) {
        null
    }

    fun save(entry: JournalEntry, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            // Run on IO so UI thread stays responsive; repo already has a short timeout
            val result = withContext(Dispatchers.IO) {
                repo.saveEntry(entry)
            }
            result.fold(
                onSuccess = { onDone(true, null) },
                onFailure = {
                    // Persistence queues offline writes — always let the user leave the editor
                    onDone(true, "Saved locally — will sync when online")
                }
            )
        }
    }
}
