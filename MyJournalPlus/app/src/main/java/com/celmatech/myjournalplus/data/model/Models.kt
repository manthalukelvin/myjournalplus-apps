package com.celmatech.myjournalplus.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class UserProfile(
    @DocumentId val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val firstName: String = "",
    val surname: String = "",
    val gender: String = "",
    val age: Int? = null,
    val isPremium: Boolean = false,
    val premiumSource: String? = null,
    val role: String = "user",
    val photoUrl: String? = null,
    val createdAt: Timestamp? = null,
    val moodEnabled: Boolean = true
)

data class JournalEntry(
    @DocumentId val id: String = "",
    val uid: String = "",
    val title: String = "",
    val content: String = "",
    val mood: String? = null,
    val moodScore: Int? = null,
    val tags: List<String> = emptyList(),
    val wordCount: Int = 0,
    val isFavorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val localOnly: Boolean = false
)

data class MoodLog(
    @DocumentId val id: String = "",
    val uid: String = "",
    val mood: String = "",
    val score: Int = 3,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class MoodType(val label: String, val emoji: String) {
    HAPPY("Happy", "😊"),
    GRATEFUL("Grateful", "🙏"),
    CALM("Calm", "😌"),
    NEUTRAL("Neutral", "😐"),
    ANXIOUS("Anxious", "😰"),
    SAD("Sad", "😢"),
    ANGRY("Angry", "😠"),
    TIRED("Tired", "😴")
}

enum class Gender(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
    NON_BINARY("Non-binary"),
    PREFER_NOT("Prefer not to say"),
    OTHER("Other")
}
