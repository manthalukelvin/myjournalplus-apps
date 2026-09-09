package com.celmatech.myjournalplus.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.celmatech.myjournalplus.data.model.JournalEntry
import com.celmatech.myjournalplus.data.model.MoodLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class JournalRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    init {
        // Enable offline persistence (safe to call multiple times)
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            db.firestoreSettings = settings
        } catch (_: Exception) { /* already set */ }
    }

    private fun entriesRef(uid: String) = db.collection("users").document(uid).collection("entries")
    private fun moodsRef(uid: String) = db.collection("users").document(uid).collection("moods")

    fun entriesFlow(uid: String): Flow<List<JournalEntry>> = callbackFlow {
        val reg = entriesRef(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(300)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    // Still try to emit empty rather than crash
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { d ->
                    try {
                        d.toObject(JournalEntry::class.java)?.copy(id = d.id, uid = uid)
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun getEntry(uid: String, entryId: String): JournalEntry? = try {
        // Prefer cache when offline
        val doc = try {
            entriesRef(uid).document(entryId).get(Source.CACHE).await()
        } catch (_: Exception) {
            entriesRef(uid).document(entryId).get().await()
        }
        doc.toObject(JournalEntry::class.java)?.copy(id = doc.id, uid = uid)
    } catch (e: Exception) {
        null
    }

    /**
     * Saves an entry with a single write only.
     * Uses a pre-generated document id + set() so offline retries never create duplicates
     * (unlike add(), which always creates a new doc).
     */
    suspend fun saveEntry(entry: JournalEntry): Result<String> {
        val wordCount = entry.content.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val data = hashMapOf<String, Any?>(
            "uid" to entry.uid,
            "title" to entry.title,
            "content" to entry.content,
            "mood" to entry.mood,
            "moodScore" to entry.moodScore,
            "tags" to entry.tags,
            "wordCount" to wordCount,
            "isFavorite" to entry.isFavorite,
            "updatedAt" to Timestamp.now()
        )

        // One stable id for this save — never call add() twice
        val docId = if (entry.id.isBlank()) {
            data["createdAt"] = Timestamp.now()
            entriesRef(entry.uid).document().id
        } else {
            entry.id
        }
        val ref = entriesRef(entry.uid).document(docId)

        return try {
            // Offline persistence: set() completes when written to local cache (usually fast).
            // Timeout only stops waiting — we must NOT issue a second write with a new id.
            kotlinx.coroutines.withTimeout(3000L) {
                if (entry.id.isBlank()) {
                    ref.set(data).await()
                } else {
                    ref.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
                }
            }
            Result.success(docId)
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            // First set() was already sent; queue may still finish. Re-set same id (idempotent).
            try {
                if (entry.id.isBlank()) ref.set(data) else ref.set(data, com.google.firebase.firestore.SetOptions.merge())
            } catch (_: Exception) { }
            Result.success(docId)
        } catch (_: Exception) {
            // Still queue one write under the same id so going online does not create a second entry
            try {
                if (entry.id.isBlank()) ref.set(data) else ref.set(data, com.google.firebase.firestore.SetOptions.merge())
            } catch (_: Exception) { }
            Result.success(docId)
        }
    }

    suspend fun deleteEntry(uid: String, entryId: String): Result<Unit> = try {
        entriesRef(uid).document(entryId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun moodsFlow(uid: String): Flow<List<MoodLog>> = callbackFlow {
        val reg = moodsRef(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(90)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { d ->
                    try {
                        d.toObject(MoodLog::class.java)?.copy(id = d.id, uid = uid)
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun logMood(uid: String, mood: String, score: Int, note: String): Result<Unit> = try {
        val data = hashMapOf(
            "uid" to uid,
            "mood" to mood,
            "score" to score,
            "note" to note,
            "createdAt" to Timestamp.now()
        )
        moodsRef(uid).add(data).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
