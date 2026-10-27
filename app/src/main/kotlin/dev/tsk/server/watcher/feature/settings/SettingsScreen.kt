package dev.tsk.server.watcher.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tsk.server.watcher.feature.auth.TotpSetupForm
import dev.tsk.server.watcher.ui.components.ConfirmationDialog
import dev.tsk.server.watcher.ui.design.AppBackground
import dev.tsk.server.watcher.ui.design.IconBadge
import dev.tsk.server.watcher.ui.design.SwButton
import dev.tsk.server.watcher.ui.design.SwCard
import dev.tsk.server.watcher.ui.design.SwTextField
import dev.tsk.server.watcher.ui.design.SwTone
import dev.tsk.server.watcher.ui.design.TonalIconButton
import dev.tsk.server.watcher.ui.design.readable
import dev.tsk.server.watcher.ui.design.transparentTopBarColors
import dev.tsk.server.watcher.ui.theme.sw

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

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    colors = transparentTopBarColors(),
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        TonalIconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (!state.isEditing) {
                            TextButton(onClick = viewModel::startEditing, modifier = Modifier.padding(end = 8.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.padding(end = 6.dp).height(18.dp))
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
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SwCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SectionHeader(
                            icon = Icons.Default.Cloud,
                            tint = MaterialTheme.colorScheme.secondary,
                            title = "AWS configuration",
                            subtitle = "Where this app signs in and finds your servers",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

                        if (state.isEditing) {
                            SwTextField(
                                value = state.userPoolId,
                                onValueChange = { viewModel.update(userPoolId = it) },
                                label = { Text("User Pool ID") },
                                placeholder = { Text("us-east-1_XXXXXXXXX") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            SwTextField(
                                value = state.clientId,
                                onValueChange = { viewModel.update(clientId = it) },
                                label = { Text("App Client ID") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            SwTextField(
                                value = state.identityPoolId,
                                onValueChange = { viewModel.update(identityPoolId = it) },
                                label = { Text("Identity Pool ID") },
                                placeholder = { Text("us-east-1:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            SwTextField(
                                value = state.region,
                                onValueChange = { viewModel.update(region = it) },
                                label = { Text("AWS Region") },
                                placeholder = { Text("us-east-1") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = viewModel::cancelEditing) { Text("Cancel") }
                                SwButton(
                                    text = "Save",
                                    onClick = viewModel::saveConfig,
                                    enabled = state.userPoolId.isNotBlank() &&
                                            state.clientId.isNotBlank() &&
                                            state.identityPoolId.isNotBlank() &&
                                            state.region.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                )
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
                                    color = MaterialTheme.colorScheme.primary.readable(),
                                )
                            }
                        }
                    }
                }

                SwCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = if (mfa.enabled) MaterialTheme.sw.success else null,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SectionHeader(
                            icon = Icons.Default.Security,
                            tint = if (mfa.enabled) MaterialTheme.sw.success else MaterialTheme.sw.warning,
                            title = "Two-factor authentication",
                            subtitle = if (mfa.enabled) "On, protecting your account" else "Off, sign-in uses a password only",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        Text(
                            "Protect your account with a code from an authenticator app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (mfa.enabled) {
                            Text(
                                "Authenticator enabled. You will be asked for a code the next time you sign in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.sw.success.readable(),
                            )
                        }
                        if (mfa.error != null && mfa.setup == null) {
                            Text(mfa.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        SwButton(
                            text = if (mfa.enabled) "Set up a new authenticator" else "Set up authenticator app",
                            onClick = viewModel::startMfaSetup,
                            enabled = !mfa.isLoading,
                            loading = mfa.isLoading,
                            tone = SwTone.Tonal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                SwButton(
                    text = "Sign out",
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    tone = SwTone.Danger,
                    icon = Icons.AutoMirrored.Filled.Logout,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconBadge(icon = icon, tint = tint, size = 44.dp)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}
