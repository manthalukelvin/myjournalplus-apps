package com.celmatech.myjournalplus.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.celmatech.myjournalplus.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> = try {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: return Result.failure(Exception("Sign-in failed"))
        ensureUserProfile(user)
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(friendlyAuthError(e))
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        firstName: String,
        surname: String,
        gender: String,
        age: Int?
    ): Result<FirebaseUser> = try {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: return Result.failure(Exception("Sign-up failed"))
        val profile = hashMapOf<String, Any?>(
            "email" to email.trim(),
            "displayName" to displayName.ifBlank {
                listOf(firstName, surname).filter { it.isNotBlank() }.joinToString(" ")
                    .ifBlank { email.substringBefore("@") }
            },
            "firstName" to firstName.trim(),
            "surname" to surname.trim(),
            "gender" to gender,
            "age" to age,
            "isPremium" to false,
            "role" to "user",
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        db.collection("users").document(user.uid).set(profile).await()
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(friendlyAuthError(e))
    }

    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> = try {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val user = result.user ?: return Result.failure(Exception("Google sign-in failed"))
        ensureUserProfile(user)
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(friendlyAuthError(e))
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(friendlyAuthError(e))
    }

    private suspend fun ensureUserProfile(user: FirebaseUser) {
        try {
            val doc = db.collection("users").document(user.uid).get().await()
            if (!doc.exists()) {
                val profile = hashMapOf(
                    "email" to (user.email ?: ""),
                    "displayName" to (user.displayName ?: user.email?.substringBefore("@") ?: "User"),
                    "firstName" to "",
                    "surname" to "",
                    "gender" to "",
                    "isPremium" to false,
                    "role" to "user",
                    "photoUrl" to user.photoUrl?.toString(),
                    "createdAt" to com.google.firebase.Timestamp.now()
                )
                db.collection("users").document(user.uid).set(profile).await()
            }
        } catch (_: Exception) { }
    }

    /** Robust parse: handles boolean true, string "true", number 1, etc. */
    fun parseUserProfile(uid: String, data: Map<String, Any>?): UserProfile? {
        if (data == null) return null
        val premiumRaw = data["isPremium"]
        val isPremium = when (premiumRaw) {
            is Boolean -> premiumRaw
            is String -> premiumRaw.equals("true", ignoreCase = true) || premiumRaw == "1"
            is Number -> premiumRaw.toInt() != 0
            else -> false
        }
        val ageRaw = data["age"]
        val age = when (ageRaw) {
            is Number -> ageRaw.toInt()
            is String -> ageRaw.toIntOrNull()
            else -> null
        }
        return UserProfile(
            uid = uid,
            email = data["email"] as? String ?: "",
            displayName = data["displayName"] as? String ?: "",
            firstName = data["firstName"] as? String ?: "",
            surname = data["surname"] as? String ?: "",
            gender = data["gender"] as? String ?: "",
            age = age,
            isPremium = isPremium,
            premiumSource = data["premiumSource"] as? String,
            role = data["role"] as? String ?: "user",
            photoUrl = data["photoUrl"] as? String,
            createdAt = data["createdAt"] as? com.google.firebase.Timestamp,
            moodEnabled = (data["moodEnabled"] as? Boolean) ?: true
        )
    }

    suspend fun getUserProfile(uid: String): UserProfile? = try {
        // Prefer server so admin premium updates show quickly
        val doc = try {
            db.collection("users").document(uid).get(Source.SERVER).await()
        } catch (_: Exception) {
            db.collection("users").document(uid).get().await()
        }
        parseUserProfile(uid, doc.data)
    } catch (e: Exception) {
        null
    }

    fun userProfileFlow(uid: String): Flow<UserProfile?> = callbackFlow {
        val reg = db.collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(parseUserProfile(uid, snap.data))
            }
        awaitClose { reg.remove() }
    }

    /** Force refresh profile from server (call after granting premium). */
    suspend fun refreshProfileFromServer(uid: String): UserProfile? = getUserProfile(uid)

    suspend fun signOut() {
        try { auth.signOut() } catch (_: Exception) {}
    }

    private fun friendlyAuthError(e: Exception): Exception {
        val msg = e.message?.lowercase() ?: ""
        return when {
            "password is invalid" in msg || "wrong-password" in msg ->
                Exception("Incorrect password. Please try again.")
            "no user record" in msg || "user-not-found" in msg ->
                Exception("No account found with this email.")
            "email address is badly formatted" in msg ->
                Exception("Please enter a valid email address.")
            "email-already-in-use" in msg ->
                Exception("An account with this email already exists.")
            "weak-password" in msg ->
                Exception("Password should be at least 6 characters.")
            "network" in msg ->
                Exception("Network error. Check your connection.")
            "too-many-requests" in msg ->
                Exception("Too many attempts. Please wait a moment.")
            else -> Exception(e.message ?: "Something went wrong. Please try again.")
        }
    }
}
