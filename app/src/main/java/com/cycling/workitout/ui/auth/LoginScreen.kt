package com.cycling.workitout.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.ui.theme.WorkItOutTheme

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onGoogleSignIn: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        LoginScreenContent(
            state = state,
            onEmailChange = viewModel::setEmail,
            onPasswordChange = viewModel::setPassword,
            onDisplayNameChange = viewModel::setDisplayName,
            onToggleShowPassword = viewModel::toggleShowPassword,
            onToggleMode = viewModel::toggleMode,
            onSubmit = viewModel::submit,
            onContinueAnonymously = viewModel::continueAnonymously,
            onGoogleSignIn = onGoogleSignIn,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun LoginScreenContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onToggleShowPassword: () -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit,
    onContinueAnonymously: () -> Unit,
    onGoogleSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    )
    {
        Text(
            text = if (state.mode == LoginUiState.Mode.SignIn)
                "Welcome Back" else "Create Account",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

//            Email
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        val toggleIcon =
            if (state.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
        val toggleDescription = if (state.showPassword) "Hide password" else "Show password"

//            Password
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (state.showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(
                    onClick = onToggleShowPassword
                ) {
                    Icon(
                        imageVector = toggleIcon,
                        contentDescription = toggleDescription
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

//            Display name
        if (state.mode == LoginUiState.Mode.SignUp) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Display name (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        state.errorMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically

                ) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (state.mode == LoginUiState.Mode.SignIn) "Sign In" else "Create Account"
                )
            }
        }

        TextButton(
            onClick = onToggleMode,
        ) {
            Text(
                text = if (state.mode == LoginUiState.Mode.SignIn) {
                    "Don't have an account? Sign up"
                } else {
                    "Already have an account? Sign in"
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                text = "or",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier.weight(1f))
        }

        OutlinedButton(
            onClick = onGoogleSignIn,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign in with Google")
        }
        Spacer(Modifier.height(4.dp))

        TextButton(
            onClick = onContinueAnonymously,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue without signing in")
        }
    }
}


// ─── Previews ────────────────────────────────────────────────────────────────

@Composable
private fun LoginScreenPreviewSurface(state: LoginUiState) {
    WorkItOutTheme {
        Scaffold { innerPadding ->
            LoginScreenContent(
                state = state,
                onEmailChange = {},
                onPasswordChange = {},
                onDisplayNameChange = {},
                onToggleShowPassword = {},
                onToggleMode = {},
                onSubmit = {},
                onContinueAnonymously = {},
                onGoogleSignIn = {},
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Preview(showBackground = true, name = "Login – idle")
@Composable
private fun LoginScreenPreviewIdle() {
    LoginScreenPreviewSurface(
        state = LoginUiState(
            email = "rider@example.com",
            password = "secret123"
        )
    )
}

@Preview(showBackground = true, name = "Login – error")
@Composable
private fun LoginScreenPreviewError() {
    LoginScreenPreviewSurface(
        state = LoginUiState(
            email = "rider@example.com",
            password = "wrongpass",
            errorMessage = "Wrong email or password"
        )
    )
}

@Preview(showBackground = true, name = "Login – submitting")
@Composable
private fun LoginScreenPreviewSubmitting() {
    LoginScreenPreviewSurface(
        state = LoginUiState(
            email = "rider@example.com",
            password = "secret123",
            isSubmitting = true
        )
    )
}

@Preview(showBackground = true, name = "Sign up – with error")
@Composable
private fun LoginScreenPreviewSignupError() {
    LoginScreenPreviewSurface(
        state = LoginUiState(
            mode = LoginUiState.Mode.SignUp,
            email = "rider@example.com",
            password = "abc",
            displayName = "Shay",
            errorMessage = "Password must be at least 6 characters"
        )
    )
}