package dev.tsk.server.watcher.feature.instancedetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tsk.server.watcher.core.domain.model.Ec2State
import dev.tsk.server.watcher.core.domain.model.InstanceConfig
import dev.tsk.server.watcher.ui.components.ConfirmationDialog
import dev.tsk.server.watcher.ui.components.ErrorState
import dev.tsk.server.watcher.ui.components.UiState
import dev.tsk.server.watcher.ui.design.AppBackground
import dev.tsk.server.watcher.ui.design.IconBadge
import dev.tsk.server.watcher.ui.design.ShimmerChip
import dev.tsk.server.watcher.ui.design.SkeletonList
import dev.tsk.server.watcher.ui.design.StatusOrb
import dev.tsk.server.watcher.ui.design.SwButton
import dev.tsk.server.watcher.ui.design.SwCard
import dev.tsk.server.watcher.ui.design.SwTextField
import dev.tsk.server.watcher.ui.design.SwTone
import dev.tsk.server.watcher.ui.design.TonalIconButton
import dev.tsk.server.watcher.ui.design.readable
import dev.tsk.server.watcher.ui.design.transparentTopBarColors
import dev.tsk.server.watcher.ui.theme.StatePending
import dev.tsk.server.watcher.ui.theme.StateRunning
import dev.tsk.server.watcher.ui.theme.StateStopped
import dev.tsk.server.watcher.ui.theme.StateError
import dev.tsk.server.watcher.ui.theme.StateUnknown
import dev.tsk.server.watcher.ui.theme.sw

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceDetailScreen(
    onBack: () -> Unit,
    viewModel: InstanceDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: error("Could not read file")
            val json = org.json.JSONObject(text)
            val map = json.keys().asSequence().associateWith { json.getString(it) }
            viewModel.importConfigs(map)
        }.onFailure { e ->
            viewModel.showImportError(e.message)
        }
    }
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

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    colors = transparentTopBarColors(),
                    title = { Text(screenTitle ?: "Instance", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        TonalIconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            TonalIconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Instance actions")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Instance") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showEditInstanceDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Instance", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
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
                is UiState.Loading -> SkeletonList(
                    count = 2,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                is UiState.Error -> ErrorState(
                    message = state.message,
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(padding),
                )

                is UiState.Success -> {
                    val data = state.data
                    val sw = MaterialTheme.sw
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatusHero(data)

                        val isOn = data.ec2State.isOn
                        SwButton(
                            text = if (isOn) "Stop instance" else "Start instance",
                            onClick = { showToggleConfirmation = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !data.isStatusLoading &&
                                    !data.isToggling &&
                                    data.ec2State != Ec2State.PENDING &&
                                    data.ec2State != Ec2State.STOPPING &&
                                    data.ec2State != Ec2State.SHUTTING_DOWN,
                            loading = data.isToggling,
                            tone = if (isOn) SwTone.Danger else SwTone.Primary,
                            icon = if (isOn) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow,
                        )

                        SwCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                IconBadge(Icons.Default.ShowChart, sw.info, size = 44.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "CloudWatch metrics integration coming soon",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        SwCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconBadge(Icons.Default.Tune, MaterialTheme.colorScheme.tertiary, size = 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Config variables", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${configs.size} ${if (configs.size == 1) "variable" else "variables"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                                        Icon(Icons.Default.Upload, contentDescription = "Import from JSON", modifier = Modifier.size(22.dp))
                                    }
                                    IconButton(onClick = { showAddConfigDialog = true }) {
                                        Icon(Icons.Default.Add, contentDescription = "Add variable", modifier = Modifier.size(22.dp))
                                    }
                                }
                                if (configs.isEmpty()) {
                                    Text(
                                        "No variables yet. Tap + to add one.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                    )
                                } else {
                                    Spacer(Modifier.height(8.dp))
                                    configs.forEachIndexed { index, cfg ->
                                        if (index > 0) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                                                Text(
                                                    cfg.key,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary.readable(),
                                                )
                                                Text(
                                                    cfg.value.ifEmpty { "(empty)" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
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

                        val canPush = data.ec2State == Ec2State.STOPPED && !data.isPushingConfig
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SwButton(
                                text = "Push config to EC2",
                                onClick = viewModel::pushConfig,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canPush,
                                loading = data.isPushingConfig,
                                tone = SwTone.Tonal,
                                icon = Icons.Default.CloudUpload,
                            )
                            if (data.ec2State != Ec2State.STOPPED) {
                                Text(
                                    "Stop the instance first, then push. Env vars apply on next start.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sw.warning.readable(),
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            } else {
                                Text(
                                    "After pushing, start the instance. Env vars are written on boot.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }

                        if (data.domainHostedZoneId != null) {
                            val canUpdateDns = data.ec2State == Ec2State.RUNNING && data.publicIp != null && !data.isDnsUpdating
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SwButton(
                                    text = "Update DNS A record",
                                    onClick = viewModel::updateDnsRecord,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = canUpdateDns,
                                    loading = data.isDnsUpdating,
                                    tone = SwTone.Tonal,
                                    icon = Icons.Default.Language,
                                )
                                if (data.ec2State != Ec2State.RUNNING) {
                                    Text(
                                        "Instance must be running to update DNS.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/** Top card: the instance's current state front and center, then its identifying details. */
@Composable
private fun StatusHero(data: InstanceData) {
    val sw = MaterialTheme.sw
    val color = when {
        data.isStatusLoading -> StateUnknown
        data.ec2State == Ec2State.RUNNING -> StateRunning
        data.ec2State == Ec2State.STOPPED -> StateStopped
        data.ec2State.isTransitional -> StatePending
        data.ec2State == Ec2State.TERMINATED -> StateError
        else -> StateUnknown
    }
    val glows = !data.isStatusLoading && (data.ec2State == Ec2State.RUNNING || data.ec2State.isTransitional)

    SwCard(modifier = Modifier.fillMaxWidth(), accent = if (glows) color else null) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusOrb(color = color, size = 14.dp, pulse = glows)
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            data.isStatusLoading -> "Checking…"
                            else -> data.ec2State.displayName
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = color.readable(),
                    )
                    Text(
                        data.label.ifEmpty { data.instanceId },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (data.isStatusLoading) {
                    ShimmerChip()
                } else if (data.isPrimary) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(sw.star.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = sw.star)
                        Text(
                            "Primary",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = sw.star,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow(Icons.Default.Dns, "Instance ID", data.instanceId.ifEmpty { "—" }, monospace = true)
                InfoRow(Icons.Default.Public, "Region", data.region.ifEmpty { "—" })
                InfoRow(
                    Icons.Default.Language,
                    "Public IP",
                    if (data.isStatusLoading) "loading…" else data.publicIp ?: "—",
                    monospace = true,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, monospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
                SwTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Key") },
                    placeholder = { Text("e.g. SW_DOMAIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SwTextField(
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
                SwTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SwTextField(
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
