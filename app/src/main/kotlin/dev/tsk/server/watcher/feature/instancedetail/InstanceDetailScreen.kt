package dev.tsk.server.watcher.feature.instancedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tsk.server.watcher.core.domain.model.Ec2State
import dev.tsk.server.watcher.core.domain.model.InstanceConfig
import dev.tsk.server.watcher.ui.components.ConfirmationDialog
import dev.tsk.server.watcher.ui.components.ErrorState
import dev.tsk.server.watcher.ui.components.UiState
import dev.tsk.server.watcher.ui.design.Ec2StateChip
import dev.tsk.server.watcher.ui.theme.StateError
import dev.tsk.server.watcher.ui.theme.StateRunning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceDetailScreen(
    onBack: () -> Unit,
    viewModel: InstanceDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    var showToggleConfirmation by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var pendingDeleteInstance by remember { mutableStateOf(false) }
    var showEditInstanceDialog by remember { mutableStateOf(false) }
    var showAddConfigDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<InstanceConfig?>(null) }
    var pendingDeleteConfig by remember { mutableStateOf<InstanceConfig?>(null) }

    val screenTitle = (uiState as? UiState.Success)?.data?.label?.ifEmpty { (uiState as? UiState.Success)?.data?.instanceId } ?: "Instance"

    if (showToggleConfirmation) {
        val data = (uiState as? UiState.Success)?.data
        if (data != null) {
            val isOn = data.ec2State.isOn
            ConfirmationDialog(
                title = if (isOn) "Stop Instance" else "Start Instance",
                message = "Are you sure you want to ${if (isOn) "stop" else "start"} ${data.label.ifEmpty { data.instanceId }}?",
                confirmText = if (isOn) "Stop" else "Start",
                isDestructive = isOn,
                onConfirm = viewModel::toggleInstance,
                onDismiss = { showToggleConfirmation = false },
            )
        }
    }

    if (showEditInstanceDialog) {
        val data = (uiState as? UiState.Success)?.data
        if (data != null) {
            EditInstanceDialog(
                currentLabel = data.label,
                currentRegion = data.region,
                onSave = { label, region ->
                    viewModel.updateInstance(label, region)
                    showEditInstanceDialog = false
                },
                onDismiss = { showEditInstanceDialog = false },
            )
        }
    }

    if (showAddConfigDialog) {
        AddEditConfigDialog(
            config = null,
            onSave = { key, value ->
                viewModel.addConfig(key, value)
                showAddConfigDialog = false
            },
            onDismiss = { showAddConfigDialog = false },
        )
    }

    editingConfig?.let { cfg ->
        AddEditConfigDialog(
            config = cfg,
            onSave = { key, value ->
                viewModel.updateConfig(cfg.id, key, value)
                editingConfig = null
            },
            onDismiss = { editingConfig = null },
        )
    }

    pendingDeleteConfig?.let { cfg ->
        ConfirmationDialog(
            title = "Delete Variable",
            message = "Remove \"${cfg.key}\" from config?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteConfig(cfg.id)
                pendingDeleteConfig = null
            },
            onDismiss = { pendingDeleteConfig = null },
        )
    }

    if (pendingDeleteInstance) {
        ConfirmationDialog(
            title = "Delete Instance",
            message = "Remove this EC2 instance from monitoring? The actual AWS instance will not be stopped.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = { viewModel.deleteInstance { onBack() } },
            onDismiss = { pendingDeleteInstance = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle ?: "Instance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Instance actions")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Instance") },
                                onClick = {
                                    showMenu = false
                                    showEditInstanceDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Instance") },
                                onClick = {
                                    showMenu = false
                                    pendingDeleteInstance = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is UiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is UiState.Error -> ErrorState(
                message = state.message,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(padding),
            )

            is UiState.Success -> {
                val data = state.data
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
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            InfoRow("Instance ID", data.instanceId.ifEmpty { "—" })
                            InfoRow("Region", data.region.ifEmpty { "—" })
                            InfoRow("Public IP", data.publicIp ?: "—")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Ec2StateChip(data.ec2State)
                                if (data.isPrimary) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = "Primary",
                                            modifier = Modifier.size(14.dp),
                                            tint = Color(0xFFFFD180),
                                        )
                                        Text(
                                            "Primary",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFFFD180),
                                            modifier = Modifier.padding(start = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val isOn = data.ec2State.isOn
                    Button(
                        onClick = { showToggleConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !data.isToggling &&
                                data.ec2State != Ec2State.PENDING &&
                                data.ec2State != Ec2State.STOPPING &&
                                data.ec2State != Ec2State.SHUTTING_DOWN,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isOn) StateError else StateRunning),
                    ) {
                        if (data.isToggling) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (isOn) "Stop Instance" else "Start Instance", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(2.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "CloudWatch metrics integration coming soon",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(2.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Config Variables", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                IconButton(onClick = { showAddConfigDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add variable", modifier = Modifier.size(20.dp))
                                }
                            }
                            if (configs.isEmpty()) {
                                Text(
                                    "No variables yet. Tap + to add one.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                configs.forEachIndexed { index, cfg ->
                                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(cfg.key, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(cfg.value.ifEmpty { "(empty)" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row {
                                            IconButton(onClick = { editingConfig = cfg }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = { pendingDeleteConfig = cfg }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val canPush = data.ec2State == Ec2State.STOPPED && !data.isPushingConfig
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = viewModel::pushConfig,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canPush,
                        ) {
                            if (data.isPushingConfig) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Push Config to EC2", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (data.ec2State != Ec2State.STOPPED) {
                            Text(
                                "Instance must be stopped to push config",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFD180),
                            )
                        }
                    }

                    if (data.domainHostedZoneId != null) {
                        val canUpdateDns = data.ec2State == Ec2State.RUNNING && data.publicIp != null && !data.isDnsUpdating
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = viewModel::updateDnsRecord,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canUpdateDns,
                            ) {
                                if (data.isDnsUpdating) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Update DNS A Record", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (data.ec2State != Ec2State.RUNNING) {
                                Text(
                                    "Instance must be running to update DNS",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AddEditConfigDialog(
    config: InstanceConfig?,
    onSave: (key: String, value: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf(config?.key ?: "") }
    var value by remember { mutableStateOf(config?.value ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (config == null) "Add Variable" else "Edit Variable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Key") },
                    placeholder = { Text("e.g. SW_DOMAIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(key.trim(), value.trim()) },
                enabled = key.isNotBlank(),
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditInstanceDialog(
    currentLabel: String,
    currentRegion: String,
    onSave: (label: String, region: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(currentLabel) }
    var region by remember { mutableStateOf(currentRegion) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Instance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = { Text("AWS Region") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label.trim(), region.trim()) },
                enabled = label.isNotBlank() && region.isNotBlank(),
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
