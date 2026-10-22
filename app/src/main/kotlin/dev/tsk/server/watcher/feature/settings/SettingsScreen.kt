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
