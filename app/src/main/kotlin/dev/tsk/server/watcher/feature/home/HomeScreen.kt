package dev.tsk.server.watcher.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import dev.tsk.server.watcher.R
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tsk.server.watcher.core.domain.model.HostedZoneSummary
import dev.tsk.server.watcher.ui.components.ConfirmationDialog
import dev.tsk.server.watcher.ui.components.ErrorState
import dev.tsk.server.watcher.ui.components.UiState
import dev.tsk.server.watcher.ui.design.LogoText
import dev.tsk.server.watcher.ui.theme.StateError
import dev.tsk.server.watcher.ui.theme.StatePending
import dev.tsk.server.watcher.ui.theme.StateRunning
import dev.tsk.server.watcher.ui.theme.StateUnknown
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDomainClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hostedZones by viewModel.hostedZones.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    var pendingToggle by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    pendingToggle?.let { (domainId, newState) ->
        ConfirmationDialog(
            title = if (newState) "Start All Instances" else "Stop All Instances",
            message = "Are you sure you want to ${if (newState) "start" else "stop"} all instances for this domain?",
            confirmText = if (newState) "Start" else "Stop",
            isDestructive = !newState,
            onConfirm = { viewModel.toggleMasterSwitch(domainId, newState) },
            onDismiss = { pendingToggle = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_round),
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                        LogoText()
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Domain")
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = uiState,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "homeContent",
            modifier = Modifier.fillMaxSize(),
        ) { state ->
            when (state) {
                is UiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                is UiState.Error -> ErrorState(
                    message = state.message,
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(padding),
                )

                is UiState.Success -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(state.data, key = { it.domainId }) { domain ->
                        DomainCard(
                            domain = domain,
                            onToggle = { newState -> pendingToggle = domain.domainId to newState },
                            onClick = { onDomainClick(domain.domainId) },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
        ) {
            AddDomainSheet(
                hostedZones = hostedZones,
                onAdd = { name, region, hostedZoneId, hostedZoneName ->
                    viewModel.addDomain(name, region, hostedZoneId, hostedZoneName)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showAddSheet = false }
                },
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showAddSheet = false }
                },
            )
        }
    }
}

@Composable
private fun DomainCard(
    domain: DomainUiModel,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(status = domain.status, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(domain.domainName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = statusLabel(domain),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(domain.status),
                    fontWeight = FontWeight.Medium,
                )
            }
            Switch(
                checked = domain.isMasterOn,
                onCheckedChange = onToggle,
                enabled = domain.status != DomainStatus.TRANSITIONING,
            )
        }
    }
}

@Composable
private fun StatusDot(status: DomainStatus, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    if (status == DomainStatus.ALL_RUNNING) {
        val infiniteTransition = rememberInfiniteTransition(label = "dotPulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "dotAlpha",
        )
        Canvas(modifier = modifier.size(12.dp)) { drawCircle(color = color.copy(alpha = alpha)) }
    } else {
        Canvas(modifier = modifier.size(12.dp)) { drawCircle(color = color) }
    }
}

private fun statusColor(status: DomainStatus): Color = when (status) {
    DomainStatus.ALL_RUNNING   -> StateRunning
    DomainStatus.ALL_STOPPED   -> StateError
    DomainStatus.TRANSITIONING -> StatePending
    DomainStatus.MIXED         -> StatePending
    DomainStatus.UNKNOWN       -> StateUnknown
}

private fun statusLabel(domain: DomainUiModel): String = when {
    domain.instanceCount == 0                    -> "No instances"
    domain.status == DomainStatus.ALL_RUNNING    -> "All running (${domain.runningCount})"
    domain.status == DomainStatus.ALL_STOPPED    -> "All stopped"
    domain.status == DomainStatus.TRANSITIONING  -> "Transitioning…"
    domain.status == DomainStatus.MIXED          -> "${domain.runningCount}/${domain.instanceCount} running"
    else -> "Unknown"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDomainSheet(
    hostedZones: List<HostedZoneSummary>,
    onAdd: (name: String, region: String, hostedZoneId: String?, hostedZoneName: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("us-east-1") }
    var selectedZone by remember { mutableStateOf<HostedZoneSummary?>(null) }
    var zoneDropdownExpanded by remember { mutableStateOf(false) }
    var manualZoneId by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Add Domain", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Domain Name") }, placeholder = { Text("e.g. mauli.store") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedTextField(
            value = region, onValueChange = { region = it },
            label = { Text("AWS Region") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        if (hostedZones.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = zoneDropdownExpanded,
                onExpandedChange = { zoneDropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedZone?.name ?: "None",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Hosted Zone (optional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = zoneDropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = zoneDropdownExpanded,
                    onDismissRequest = { zoneDropdownExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = { selectedZone = null; zoneDropdownExpanded = false },
                    )
                    hostedZones.forEach { zone ->
                        DropdownMenuItem(
                            text = { Text(zone.name) },
                            onClick = { selectedZone = zone; zoneDropdownExpanded = false },
                        )
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = manualZoneId, onValueChange = { manualZoneId = it },
                label = { Text("Hosted Zone ID (optional)") },
                placeholder = { Text("Z0000000000000000000") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            val effectiveZoneId = if (hostedZones.isNotEmpty()) selectedZone?.id else manualZoneId.ifBlank { null }
            val effectiveZoneName = if (hostedZones.isNotEmpty()) selectedZone?.name else null
            TextButton(
                onClick = { if (name.isNotBlank() && region.isNotBlank()) onAdd(name, region, effectiveZoneId, effectiveZoneName) },
                enabled = name.isNotBlank() && region.isNotBlank(),
            ) { Text("Add", fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(16.dp))
    }
}
