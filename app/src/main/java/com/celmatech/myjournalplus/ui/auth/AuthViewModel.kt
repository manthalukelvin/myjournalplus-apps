package com.celmatech.myjournalplus.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.celmatech.myjournalplus.data.model.UserProfile
import com.celmatech.myjournalplus.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _currentUser = MutableStateFlow<FirebaseUser?>(authRepo.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    /** One-shot event so LoginScreen can navigate after Google / email success */
    private val _loginSuccessEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginSuccessEvent: SharedFlow<Unit> = _loginSuccessEvent.asSharedFlow()

    private var profileJob: Job? = null

    init {
        viewModelScope.launch {
            authRepo.authStateFlow().collectLatest { user ->
                _currentUser.value = user
                profileJob?.cancel()
                if (user != null) {
                    profileJob = viewModelScope.launch {
                        try {
                            authRepo.userProfileFlow(user.uid).collectLatest { profile ->
                                _userProfile.value = profile
                            }
                        } catch (_: Exception) {
                            _userProfile.value = null
                        }
                    }
                } else {
                    _userProfile.value = null
                }
            }
        }
    }

    fun clearError() { _error.value = null }
    fun clearSuccess() { _successMessage.value = null }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            _error.value = "Please enter email and password"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = authRepo.signInWithEmail(email, password)
            _loading.value = false
            result.fold(
                onSuccess = {
                    _loginSuccessEvent.tryEmit(Unit)
                    onSuccess()
                },
                onFailure = { _error.value = it.message ?: "Login failed" }
            )
        }
    }

    fun signUp(
        email: String,
        password: String,
        displayName: String,
        firstName: String,
        surname: String,
        gender: String,
        age: Int?,
        onSuccess: () -> Unit
    ) {
        if (email.isBlank() || password.length < 6) {
            _error.value = "Valid email and password (min 6 chars) required"
            return
        }
        if (firstName.isBlank() || surname.isBlank()) {
            _error.value = "Please enter first name and surname"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = authRepo.signUpWithEmail(
                email, password, displayName, firstName, surname, gender, age
            )
            _loading.value = false
            result.fold(
                onSuccess = {
                    _loginSuccessEvent.tryEmit(Unit)
                    onSuccess()
                },
                onFailure = { _error.value = it.message ?: "Sign up failed" }
            )
        }
    }

    fun loginWithGoogle(idToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = authRepo.signInWithGoogle(idToken)
            _loading.value = false
            result.fold(
                onSuccess = {
                    _loginSuccessEvent.tryEmit(Unit)
                    onSuccess()
                },
                onFailure = { _error.value = it.message ?: "Google sign-in failed" }
            )
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank() || !email.contains("@")) {
            _error.value = "Enter a valid email address"
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _successMessage.value = null
            val result = authRepo.sendPasswordReset(email)
            _loading.value = false
            result.fold(
                onSuccess = {
                    _successMessage.value = "Password reset link sent to $email. Check your inbox."
                },
                onFailure = { _error.value = it.message ?: "Could not send reset email" }
            )
        }
    }

    fun refreshProfile() {
        val uid = _currentUser.value?.uid ?: return
        viewModelScope.launch {
            try {
                val profile = authRepo.refreshProfileFromServer(uid)
                if (profile != null) _userProfile.value = profile
            } catch (_: Exception) { }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepo.signOut()
            _userProfile.value = null
            _currentUser.value = null
        }
    }
}
