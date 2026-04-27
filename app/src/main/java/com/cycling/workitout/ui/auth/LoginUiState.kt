package com.cycling.workitout.ui.auth

data class LoginUiState(
    val mode: Mode = Mode.SignIn,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val showPassword: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    enum class Mode { SignIn, SignUp }
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}