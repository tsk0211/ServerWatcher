package dev.tsk.server.watcher.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tsk.server.watcher.R
import dev.tsk.server.watcher.core.data.aws.MfaMethod
import dev.tsk.server.watcher.ui.design.AppBackground
import dev.tsk.server.watcher.ui.design.IconBadge
import dev.tsk.server.watcher.ui.design.LogoWordmark
import dev.tsk.server.watcher.ui.design.SwButton
import dev.tsk.server.watcher.ui.design.SwCard
import dev.tsk.server.watcher.ui.design.SwTextField

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

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
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
}

@Composable
private fun LoginForm(isLoading: Boolean, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        Image(
            painter = painterResource(R.mipmap.ic_launcher_round),
            contentDescription = null,
            modifier = Modifier
                .size(88.dp)
                .shadow(24.dp, CircleShape, ambientColor = glow, spotColor = glow)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), CircleShape),
        )
        Spacer(Modifier.height(12.dp))
        LogoWordmark(MaterialTheme.typography.headlineMedium)
        Text(
            text = "Sign in to manage your servers",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        SwCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SwTextField(
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
                SwTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
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
                Spacer(Modifier.height(2.dp))
                PrimaryButton(
                    text = "Sign in",
                    isLoading = isLoading,
                    enabled = email.isNotBlank() && password.isNotBlank(),
                    onClick = { onLogin(email, password) },
                )
            }
        }
    }
}

@Composable
private fun PasswordToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (visible) "Hide password" else "Show password",
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

    StepCard(
        icon = Icons.Default.Security,
        title = "Two-factor authentication",
        subtitle = when (method) {
            MfaMethod.TOTP -> "Enter the 6-digit code from your authenticator app"
            MfaMethod.SMS -> "Enter the 6-digit code we sent you by text message"
        },
    ) {
        SwTextField(
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
    var showPassword by remember { mutableStateOf(false) }
    val mismatch = confirm.isNotEmpty() && confirm != password
    val canSubmit = password.length >= MIN_PASSWORD_LENGTH && password == confirm
    val transformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()

    StepCard(
        icon = Icons.Default.Lock,
        title = "Choose a new password",
        subtitle = "Your account was created with a temporary password. Set a permanent one to continue.",
    ) {
        SwTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("New password") },
            supportingText = { Text("At least $MIN_PASSWORD_LENGTH characters, with upper and lower case letters and a number") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = transformation,
            trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            singleLine = true,
        )
        SwTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm password") },
            isError = mismatch,
            supportingText = if (mismatch) ({ Text("Passwords do not match") }) else null,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = transformation,
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
    StepCard(
        icon = Icons.Default.Security,
        title = "Set up two-factor authentication",
        subtitle = null,
    ) {
        val setup = step.setup
        when {
            setup != null -> TotpSetupForm(
                secret = setup.secret,
                accountName = step.challenge.username,
                isLoading = isLoading,
                onSubmit = onSubmit,
            )
            isLoading -> CircularProgressIndicator()
            else -> SwButton(text = "Try again", onClick = onRetry, modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClick = onBack, enabled = !isLoading) { Text("Back to sign in") }
    }
}

/** One step of the sign-in flow: an icon, a heading and a hint above a card holding the controls. */
@Composable
private fun StepCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(icon = icon, tint = MaterialTheme.colorScheme.primary, size = 64.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(10.dp))
        SwCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, isLoading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    SwButton(
        text = text,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        loading = isLoading,
    )
}

private const val MIN_PASSWORD_LENGTH = 8
