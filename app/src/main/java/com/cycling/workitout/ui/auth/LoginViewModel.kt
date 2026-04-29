package com.cycling.workitout.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.data.auth.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun setEmail(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun setDisplayName(displayName: String) {
        _uiState.update { it.copy(displayName = displayName) }
    }

    fun toggleShowPassword() {
        _uiState.update { it.copy(showPassword = !it.showPassword) }
    }

    fun toggleMode() {
        _uiState.update {
            val newMode = if (it.mode == LoginUiState.Mode.SignIn) {
                LoginUiState.Mode.SignUp
            } else {
                LoginUiState.Mode.SignIn
            }
            it.copy(mode = newMode, errorMessage = null)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            val result = when (state.mode) {
                LoginUiState.Mode.SignIn -> authRepository.signInWithEmail(
                    state.email,
                    state.password
                )

                LoginUiState.Mode.SignUp -> authRepository.createAccount(
                    state.email,
                    state.password
                )
            }
            result
                .onSuccess { user ->
                    Timber.i("Auth success: uid=${user.uid}")
                    _uiState.update { it.copy(isSubmitting = false) }
                }
                .onFailure { ex ->
                    Timber.w(ex, "Auth failed")
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = messageFor(ex)
                        )
                    }
                }
        }
    }

    fun continueAnonymously() {
        if (_uiState.value.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            authRepository.signInAnonymously()
                .onSuccess { user ->
                    Timber.i("Anonymous sign-in success: uid=${user.uid}")
                    _uiState.update { it.copy(isSubmitting = false) }
                }
                .onFailure { ex ->
                    Timber.w(ex, "Anonymous sign-in failed")
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = messageFor(ex)
                        )
                    }
                }
        }
    }

    fun signInWithGoogle(idToken: String) {
        if (_uiState.value.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            authRepository.signInWithGoogle(idToken)
                .onSuccess { user ->
                    Timber.i("Google sign-in success: uid=${user.uid}")
                    _uiState.update { it.copy(isSubmitting = false) }
                }
                .onFailure { ex ->
                    Timber.w(ex, "google sign-in failed")
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = messageFor(ex)) }
                }
        }
    }

    private fun messageFor(ex: Throwable): String = when (ex) {
        is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters"
        is FirebaseAuthInvalidCredentialsException -> "Wrong email or password"
        is FirebaseAuthInvalidUserException -> "No account with that email"
        is FirebaseAuthUserCollisionException -> "An account with this email already exists"
        is FirebaseNetworkException -> "No internet — check your connection and try again"
        is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a moment and try again."
        else -> ex.message ?: "Something went wrong. Please try again."
    }
}



