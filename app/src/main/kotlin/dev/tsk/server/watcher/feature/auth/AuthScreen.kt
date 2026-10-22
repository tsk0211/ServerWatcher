package dev.tsk.server.watcher.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tsk.server.watcher.core.data.aws.MfaMethod
import dev.tsk.server.watcher.ui.design.LogoText

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.step) {
        if (uiState.step is AuthStep.Done) onAuthSuccess()
    }

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            when (val step = uiState.step) {
                is AuthStep.Mfa -> MfaForm(
                    method = step.method,
                    isLoading = uiState.isLoading,
                    onSubmit = viewModel::submitMfa,
                    onBack = viewModel::backToLogin,
                )
                is AuthStep.NewPassword -> NewPasswordForm(
                    isLoading = uiState.isLoading,
                    onSubmit = viewModel::setNewPassword,
                    onBack = viewModel::backToLogin,
                )
                is AuthStep.MfaSetup -> MfaSetupForm(
                    step = step,
                    isLoading = uiState.isLoading,
                    onSubmit = viewModel::submitTotpSetup,
                    onRetry = viewModel::retryTotpSetup,
                    onBack = viewModel::backToLogin,
                )
                AuthStep.Credentials, AuthStep.Done -> LoginForm(
                    isLoading = uiState.isLoading,
                    onLogin = viewModel::login,
                )
            }
        }
    }
}

@Composable
private fun LoginForm(isLoading: Boolean, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LogoText()
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Sign in to manage your servers",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
            singleLine = true,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (email.isNotBlank() && password.isNotBlank() && !isLoading) onLogin(email, password)
                },
            ),
            singleLine = true,
        )
        PrimaryButton(
            text = "Sign In",
            isLoading = isLoading,
            enabled = email.isNotBlank() && password.isNotBlank(),
            onClick = { onLogin(email, password) },
        )
    }
}

@Composable
private fun MfaForm(
    method: MfaMethod,
    isLoading: Boolean,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    StepColumn {
        Text(
            text = "Two-Factor Authentication",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = when (method) {
                MfaMethod.TOTP -> "Enter the 6-digit code from your authenticator app"
                MfaMethod.SMS -> "Enter the 6-digit code we sent you by text message"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
            label = { Text("Authentication Code") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (code.length == 6 && !isLoading) onSubmit(code) },
            ),
            singleLine = true,
        )
        PrimaryButton(
            text = "Verify",
            isLoading = isLoading,
            enabled = code.length == 6,
            onClick = { onSubmit(code) },
        )
        TextButton(onClick = onBack, enabled = !isLoading) { Text("Back to sign in") }
    }
}

@Composable
private fun NewPasswordForm(
    isLoading: Boolean,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && confirm != password
    val canSubmit = password.length >= MIN_PASSWORD_LENGTH && password == confirm

    StepColumn {
        Text(
            text = "Choose a new password",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Your account was created with a temporary password. Set a permanent one to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("New password") },
            supportingText = { Text("At least $MIN_PASSWORD_LENGTH characters, with upper and lower case letters and a number") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            singleLine = true,
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm password") },
            isError = mismatch,
            supportingText = if (mismatch) ({ Text("Passwords do not match") }) else null,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit && !isLoading) onSubmit(password) }),
            singleLine = true,
        )
        PrimaryButton(
            text = "Set password",
            isLoading = isLoading,
            enabled = canSubmit,
            onClick = { onSubmit(password) },
        )
        TextButton(onClick = onBack, enabled = !isLoading) { Text("Back to sign in") }
    }
}

@Composable
private fun MfaSetupForm(
    step: AuthStep.MfaSetup,
    isLoading: Boolean,
    onSubmit: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    StepColumn {
        Text(
            text = "Set up two-factor authentication",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        val setup = step.setup
        when {
            setup != null -> TotpSetupForm(
                secret = setup.secret,
                accountName = step.challenge.username,
                isLoading = isLoading,
                onSubmit = onSubmit,
            )
            isLoading -> CircularProgressIndicator()
            else -> Button(onClick = onRetry) { Text("Try again") }
        }
        TextButton(onClick = onBack, enabled = !isLoading) { Text("Back to sign in") }
    }
}

@Composable
private fun StepColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

@Composable
private fun PrimaryButton(text: String, isLoading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading && enabled,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

private const val MIN_PASSWORD_LENGTH = 8
