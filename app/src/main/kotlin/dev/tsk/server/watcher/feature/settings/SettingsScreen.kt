package dev.tsk.server.watcher.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tsk.server.watcher.feature.auth.TotpSetupForm
import dev.tsk.server.watcher.ui.components.ConfirmationDialog
import dev.tsk.server.watcher.ui.theme.StateError
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mfa by viewModel.mfa.collectAsStateWithLifecycle()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    if (showLogoutConfirm) {
        ConfirmationDialog(
            title = "Sign Out",
            message = "You will need to sign in again to access your instances.",
            confirmText = "Sign Out",
            isDestructive = true,
            onConfirm = {
                viewModel.logout()
                onLogout()
            },
            onDismiss = { showLogoutConfirm = false },
        )
    }

    mfa.setup?.let { setup ->
        AlertDialog(
            onDismissRequest = { if (!mfa.isLoading) viewModel.dismissMfaSetup() },
            title = { Text("Set up authenticator") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    TotpSetupForm(
                        secret = setup.secret,
                        accountName = viewModel.accountName,
                        isLoading = mfa.isLoading,
                        onSubmit = viewModel::verifyMfaSetup,
                    )
                    mfa.error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::dismissMfaSetup, enabled = !mfa.isLoading) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isEditing) {
                        TextButton(onClick = viewModel::startEditing) {
                            Text("Edit", fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "AWS Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    HorizontalDivider()

                    if (state.isEditing) {
                        OutlinedTextField(
                            value = state.userPoolId,
                            onValueChange = { viewModel.update(userPoolId = it) },
                            label = { Text("User Pool ID") },
                            placeholder = { Text("us-east-1_XXXXXXXXX") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.clientId,
                            onValueChange = { viewModel.update(clientId = it) },
                            label = { Text("App Client ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.identityPoolId,
                            onValueChange = { viewModel.update(identityPoolId = it) },
                            label = { Text("Identity Pool ID") },
                            placeholder = { Text("us-east-1:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.region,
                            onValueChange = { viewModel.update(region = it) },
                            label = { Text("AWS Region") },
                            placeholder = { Text("us-east-1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = viewModel::cancelEditing) { Text("Cancel") }
                            Button(
                                onClick = viewModel::saveConfig,
                                enabled = state.userPoolId.isNotBlank() &&
                                        state.clientId.isNotBlank() &&
                                        state.identityPoolId.isNotBlank() &&
                                        state.region.isNotBlank(),
                                modifier = Modifier.padding(start = 8.dp),
                            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                        }
                    } else {
                        ConfigRow("User Pool ID", state.userPoolId)
                        ConfigRow("App Client ID", state.clientId)
                        ConfigRow("Identity Pool ID", state.identityPoolId)
                        ConfigRow("AWS Region", state.region)
                        if (state.saved) {
                            Text(
                                "Saved. Restart the app for changes to take full effect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Two-factor authentication",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    HorizontalDivider()
                    Text(
                        "Protect your account with a code from an authenticator app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (mfa.enabled) {
                        Text(
                            "Authenticator enabled. You will be asked for a code the next time you sign in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (mfa.error != null && mfa.setup == null) {
                        Text(mfa.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = viewModel::startMfaSetup, enabled = !mfa.isLoading) {
                        Text(if (mfa.enabled) "Set up a new authenticator" else "Set up authenticator app")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = StateError),
            ) {
                Text("Sign Out", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
