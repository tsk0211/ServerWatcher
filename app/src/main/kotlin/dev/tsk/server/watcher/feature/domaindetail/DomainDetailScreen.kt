package dev.tsk.server.watcher.feature.domaindetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tsk.server.watcher.core.domain.model.Ec2InstanceSummary
import dev.tsk.server.watcher.core.domain.model.Ec2State
import dev.tsk.server.watcher.core.domain.model.HealthStatus
import dev.tsk.server.watcher.core.domain.model.HostedZoneSummary
import dev.tsk.server.watcher.core.domain.model.Subdomain
import dev.tsk.server.watcher.ui.components.ConfirmationDialog
import dev.tsk.server.watcher.ui.components.ErrorState
import dev.tsk.server.watcher.ui.components.UiState
import dev.tsk.server.watcher.ui.design.Ec2StateChip
import dev.tsk.server.watcher.ui.design.ShimmerChip
import kotlinx.coroutines.launch

// Subtle tinted card backgrounds for health status in dark theme
private val HealthyCardColor = Color(0xFF143020)
private val UnhealthyCardColor = Color(0xFF301414)

private fun subdomainCardColor(instances: List<InstanceRowUiModel>): Color? = when {
    instances.any { it.healthStatus is HealthStatus.Unhealthy || it.healthStatus is HealthStatus.Error } ->
        UnhealthyCardColor
    instances.any { it.healthStatus is HealthStatus.Healthy } ->
        HealthyCardColor
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainDetailScreen(
    onBack: () -> Unit,
    onInstanceClick: (String) -> Unit,
    viewModel: DomainDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hostedZones by viewModel.hostedZones.collectAsStateWithLifecycle()
    val ec2Instances by viewModel.ec2Instances.collectAsStateWithLifecycle()
    val instancesLoading by viewModel.instancesLoading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showAddSubdomainSheet by remember { mutableStateOf(false) }
    var addInstanceForSubdomainId by remember { mutableStateOf<String?>(null) }
    val subdomainSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val instanceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pendingDeleteSubdomainId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteInstanceId by remember { mutableStateOf<String?>(null) }
    var pendingToggleInstance by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var showDomainMenu by remember { mutableStateOf(false) }
    var pendingDeleteDomain by remember { mutableStateOf(false) }

    // Edit state
    var showEditDomainDialog by remember { mutableStateOf(false) }
    var pendingEditSubdomain by remember { mutableStateOf<Subdomain?>(null) }
    var pendingEditInstance by remember { mutableStateOf<InstanceRowUiModel?>(null) }

    pendingDeleteSubdomainId?.let { id ->
        ConfirmationDialog(
            title = "Delete Subdomain",
            message = "Remove this subdomain and all its instances? This cannot be undone.",
            confirmText = "Delete", isDestructive = true,
            onConfirm = { viewModel.deleteSubdomain(id) },
            onDismiss = { pendingDeleteSubdomainId = null },
        )
    }

    pendingDeleteInstanceId?.let { id ->
        ConfirmationDialog(
            title = "Delete Instance",
            message = "Remove this EC2 instance from monitoring? The actual AWS instance will not be stopped.",
            confirmText = "Delete", isDestructive = true,
            onConfirm = { viewModel.deleteInstance(id) },
            onDismiss = { pendingDeleteInstanceId = null },
        )
    }

    pendingToggleInstance?.let { (instanceId, region, newState) ->
        ConfirmationDialog(
            title = if (newState) "Start Instance" else "Stop Instance",
            message = "Are you sure you want to ${if (newState) "start" else "stop"} this EC2 instance?",
            confirmText = if (newState) "Start" else "Stop",
            isDestructive = !newState,
            onConfirm = { viewModel.toggleInstanceSwitch(instanceId, region, newState) },
            onDismiss = { pendingToggleInstance = null },
        )
    }

    if (pendingDeleteDomain) {
        ConfirmationDialog(
            title = "Delete Domain",
            message = "Remove this domain and all its subdomains and instances? This cannot be undone.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = { viewModel.deleteDomain { onBack() } },
            onDismiss = { pendingDeleteDomain = false },
        )
    }

    if (showEditDomainDialog) {
        val successData = (uiState as? UiState.Success)?.data
        EditDomainDialog(
            currentName = successData?.domainName ?: "",
            currentHostedZoneId = successData?.hostedZoneId,
            hostedZones = hostedZones,
            onSave = { name, hostedZoneId, hostedZoneName ->
                viewModel.updateDomain(name, hostedZoneId, hostedZoneName)
                showEditDomainDialog = false
            },
            onDismiss = { showEditDomainDialog = false },
        )
    }

    pendingEditSubdomain?.let { subdomain ->
        EditSubdomainDialog(
            subdomain = subdomain,
            onSave = { prefix, isApex, healthPath, rangeStart, rangeEnd, interval ->
                viewModel.updateSubdomain(subdomain.id, prefix, isApex, healthPath, rangeStart, rangeEnd, interval)
                pendingEditSubdomain = null
            },
            onDismiss = { pendingEditSubdomain = null },
        )
    }

    pendingEditInstance?.let { instance ->
        EditInstanceDialog(
            currentLabel = instance.label,
            currentRegion = instance.region,
            onSave = { label, region ->
                viewModel.updateInstance(instance.id, label, region)
                pendingEditInstance = null
            },
            onDismiss = { pendingEditInstance = null },
        )
    }

    val screenTitle = (uiState as? UiState.Success)?.data?.domainName?.ifEmpty { "Domain" } ?: "Domain"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showDomainMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Domain actions")
                        }
                        DropdownMenu(
                            expanded = showDomainMenu,
                            onDismissRequest = { showDomainMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Domain") },
                                onClick = {
                                    showDomainMenu = false
                                    showEditDomainDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Domain") },
                                onClick = {
                                    showDomainMenu = false
                                    pendingDeleteDomain = true
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState is UiState.Success) {
                FloatingActionButton(onClick = { showAddSubdomainSheet = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Subdomain")
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = uiState,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "detailContent",
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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    itemsIndexed(state.data.sections, key = { _, s -> s.subdomain.id }) { index, section ->
                        var visible by remember(section.subdomain.id) { mutableStateOf(false) }
                        LaunchedEffect(section.subdomain.id) {
                            delay(index * 60L)
                            visible = true
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(220)) + slideInVertically(
                                animationSpec = tween(220),
                                initialOffsetY = { it / 6 },
                            ),
                        ) {
                            SubdomainSection(
                                section = section,
                                hostedZoneName = state.data.hostedZoneName,
                                isStatusLoaded = state.data.isStatusLoaded,
                                onToggleInstance = { instanceId, region, newState ->
                                    pendingToggleInstance = Triple(instanceId, region, newState)
                                },
                                onDeleteInstance = { pendingDeleteInstanceId = it },
                                onDeleteSubdomain = { pendingDeleteSubdomainId = section.subdomain.id },
                                onEditSubdomain = { pendingEditSubdomain = section.subdomain },
                                onEditInstance = { pendingEditInstance = it },
                                onInstanceClick = onInstanceClick,
                                onAddInstance = { addInstanceForSubdomainId = section.subdomain.id },
                                onSetPrimaryInstance = { viewModel.setPrimaryInstance(it) },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddSubdomainSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSubdomainSheet = false }, sheetState = subdomainSheetState) {
            AddSubdomainSheet(
                onAdd = { prefix, isApex, path, rangeStart, rangeEnd, interval ->
                    viewModel.addSubdomain(prefix, isApex, path, rangeStart, rangeEnd, interval)
                    scope.launch { subdomainSheetState.hide() }.invokeOnCompletion { showAddSubdomainSheet = false }
                },
                onDismiss = {
                    scope.launch { subdomainSheetState.hide() }.invokeOnCompletion { showAddSubdomainSheet = false }
                },
            )
        }
    }

    addInstanceForSubdomainId?.let { subdomainId ->
        val defaultRegion = (uiState as? UiState.Success)?.data?.domainRegion ?: "us-east-1"
        ModalBottomSheet(onDismissRequest = { addInstanceForSubdomainId = null }, sheetState = instanceSheetState) {
            AddInstanceSheet(
                defaultRegion = defaultRegion,
                ec2Instances = ec2Instances,
                instancesLoading = instancesLoading,
                onLoadInstances = viewModel::loadEc2Instances,
                onAdd = { ec2InstanceId, label, region ->
                    viewModel.addInstance(subdomainId, ec2InstanceId, label, region)
                    scope.launch { instanceSheetState.hide() }.invokeOnCompletion { addInstanceForSubdomainId = null }
                },
                onDismiss = {
                    scope.launch { instanceSheetState.hide() }.invokeOnCompletion { addInstanceForSubdomainId = null }
                },
            )
        }
    }
}

@Composable
private fun SubdomainSection(
    section: SubdomainSectionUiModel,
    hostedZoneName: String?,
    isStatusLoaded: Boolean,
    onToggleInstance: (String, String, Boolean) -> Unit,
    onDeleteInstance: (String) -> Unit,
    onDeleteSubdomain: () -> Unit,
    onEditSubdomain: () -> Unit,
    onEditInstance: (InstanceRowUiModel) -> Unit,
    onInstanceClick: (String) -> Unit,
    onAddInstance: () -> Unit,
    onSetPrimaryInstance: (String) -> Unit,
) {
    val subdomain = section.subdomain
    val urlDisplay = when {
        subdomain.isApex -> hostedZoneName ?: subdomain.prefix.ifEmpty { "(apex)" }
        hostedZoneName != null -> "${subdomain.prefix}.$hostedZoneName"
        else -> subdomain.prefix
    }

    val containerColor = subdomainCardColor(section.instances)
        ?: MaterialTheme.colorScheme.surfaceVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            var showSubdomainMenu by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(urlDisplay, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (subdomain.healthPath.isNotBlank()) {
                        Text(
                            text = "Health: ${subdomain.healthPath} · ${subdomain.healthRangeStart}-${subdomain.healthRangeEnd} · every ${subdomain.healthIntervalSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showSubdomainMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Subdomain actions")
                    }
                    DropdownMenu(
                        expanded = showSubdomainMenu,
                        onDismissRequest = { showSubdomainMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Subdomain") },
                            onClick = {
                                showSubdomainMenu = false
                                onEditSubdomain()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Subdomain") },
                            onClick = {
                                showSubdomainMenu = false
                                onDeleteSubdomain()
                            },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            section.instances.forEach { instance ->
                InstanceRow(
                    instance = instance,
                    isStatusLoaded = isStatusLoaded,
                    onToggle = { newState -> onToggleInstance(instance.instanceId, instance.region, newState) },
                    onDelete = { onDeleteInstance(instance.id) },
                    onEdit = { onEditInstance(instance) },
                    onClick = { onInstanceClick(instance.id) },
                    onSetPrimary = { onSetPrimaryInstance(instance.id) },
                )
            }

            TextButton(
                onClick = onAddInstance,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Add EC2 Instance", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun InstanceRow(
    instance: InstanceRowUiModel,
    isStatusLoaded: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit,
    onSetPrimary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(instance.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (instance.isPrimary) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Primary",
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        tint = Color(0xFFFFD180),
                    )
                }
            }
            Text(instance.instanceId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!isStatusLoaded && instance.ec2State == Ec2State.UNKNOWN) {
                ShimmerChip()
            } else {
                Ec2StateChip(instance.ec2State)
            }
        }
        Switch(checked = instance.ec2State.isOn, onCheckedChange = onToggle)
        var showInstanceMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { showInstanceMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Instance actions")
            }
            DropdownMenu(
                expanded = showInstanceMenu,
                onDismissRequest = { showInstanceMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Edit Instance") },
                    onClick = {
                        showInstanceMenu = false
                        onEdit()
                    },
                )
                if (!instance.isPrimary) {
                    DropdownMenuItem(
                        text = { Text("Set as Primary") },
                        onClick = {
                            showInstanceMenu = false
                            onSetPrimary()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete Instance") },
                    onClick = {
                        showInstanceMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDomainDialog(
    currentName: String,
    currentHostedZoneId: String?,
    hostedZones: List<HostedZoneSummary>,
    onSave: (name: String, hostedZoneId: String?, hostedZoneName: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var selectedZone by remember {
        mutableStateOf(hostedZones.firstOrNull { it.id == currentHostedZoneId })
    }
    var zoneDropdownExpanded by remember { mutableStateOf(false) }
    var manualZoneId by remember { mutableStateOf(if (currentHostedZoneId != null && hostedZones.none { it.id == currentHostedZoneId }) currentHostedZoneId else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Domain") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Domain Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                            label = { Text("Hosted Zone") },
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
                        value = manualZoneId,
                        onValueChange = { manualZoneId = it },
                        label = { Text("Hosted Zone ID (optional)") },
                        placeholder = { Text("Z0000000000000000000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            val effectiveZoneId = if (hostedZones.isNotEmpty()) selectedZone?.id else manualZoneId.ifBlank { null }
            val effectiveZoneName = if (hostedZones.isNotEmpty()) selectedZone?.name else null
            TextButton(onClick = { onSave(name.trim(), effectiveZoneId, effectiveZoneName) }, enabled = name.isNotBlank()) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditSubdomainDialog(
    subdomain: Subdomain,
    onSave: (prefix: String, isApex: Boolean, healthPath: String, rangeStart: Int, rangeEnd: Int, interval: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var isApex by remember { mutableStateOf(subdomain.isApex) }
    var prefix by remember { mutableStateOf(subdomain.prefix) }
    var healthEnabled by remember { mutableStateOf(subdomain.healthPath.isNotBlank()) }
    var healthPath by remember { mutableStateOf(subdomain.healthPath) }
    var rangeStart by remember { mutableIntStateOf(subdomain.healthRangeStart) }
    var rangeEnd by remember { mutableIntStateOf(subdomain.healthRangeEnd) }
    var interval by remember { mutableIntStateOf(subdomain.healthIntervalSeconds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Subdomain") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Apex domain", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isApex, onCheckedChange = { isApex = it })
                }
                if (!isApex) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text("Subdomain Prefix") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable health check", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = healthEnabled, onCheckedChange = { healthEnabled = it })
                }
                if (healthEnabled) {
                    OutlinedTextField(
                        value = healthPath,
                        onValueChange = { healthPath = it },
                        label = { Text("Health Check Path") },
                        placeholder = { Text("/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rangeStart.toString(),
                            onValueChange = { rangeStart = it.toIntOrNull() ?: rangeStart },
                            label = { Text("Status Min") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = rangeEnd.toString(),
                            onValueChange = { rangeEnd = it.toIntOrNull() ?: rangeEnd },
                            label = { Text("Status Max") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    Text("Poll Interval", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 60, 120).forEach { seconds ->
                            Button(
                                onClick = { interval = seconds },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (interval == seconds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (interval == seconds) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) { Text("${seconds}s", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val path = if (healthEnabled) healthPath.ifBlank { "/" } else ""
                    onSave(if (isApex) "" else prefix.trim(), isApex, path, rangeStart, rangeEnd, interval)
                },
                enabled = isApex || prefix.isNotBlank(),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddInstanceSheet(
    defaultRegion: String,
    ec2Instances: List<Ec2InstanceSummary>,
    instancesLoading: Boolean,
    onLoadInstances: (region: String) -> Unit,
    onAdd: (ec2InstanceId: String, label: String, region: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var region by remember { mutableStateOf(defaultRegion.ifBlank { "us-east-1" }) }
    var selectedInstance by remember { mutableStateOf<Ec2InstanceSummary?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }

    // Auto-load instances when region changes (500ms debounce)
    LaunchedEffect(region) {
        if (region.isNotBlank()) {
            delay(500)
            onLoadInstances(region)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Add EC2 Instance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = region, onValueChange = { region = it.trim() },
            label = { Text("AWS Region") }, placeholder = { Text("us-east-1") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )

        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { if (!instancesLoading) dropdownExpanded = it },
        ) {
            OutlinedTextField(
                value = when {
                    instancesLoading -> "Loading…"
                    selectedInstance != null -> selectedInstance!!.dropdownLabel
                    ec2Instances.isEmpty() -> "No instances found"
                    else -> "Select an instance"
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("EC2 Instance") },
                trailingIcon = {
                    if (instancesLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                    }
                },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                enabled = !instancesLoading && ec2Instances.isNotEmpty(),
            )
            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
            ) {
                ec2Instances.forEach { inst ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    inst.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "${inst.instanceId} — ${inst.stateName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            selectedInstance = inst
                            if (label.isBlank()) label = inst.displayName
                            dropdownExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = label, onValueChange = { label = it },
            label = { Text("Label (friendly name)") }, placeholder = { Text("e.g. Web Server 1") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val id = selectedInstance?.instanceId ?: return@Button
                    onAdd(id, label.ifBlank { selectedInstance?.displayName ?: id }, region)
                },
                enabled = selectedInstance != null && region.isNotBlank(),
            ) { Text("Add Instance", fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AddSubdomainSheet(
    onAdd: (String, Boolean, String, Int, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var prefix by remember { mutableStateOf("") }
    var isApex by remember { mutableStateOf(false) }
    var healthEnabled by remember { mutableStateOf(false) }
    var path by remember { mutableStateOf("") }
    var rangeStart by remember { mutableIntStateOf(200) }
    var rangeEnd by remember { mutableIntStateOf(299) }
    var interval by remember { mutableIntStateOf(30) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Add Subdomain", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apex domain", modifier = Modifier.weight(1f))
            Switch(checked = isApex, onCheckedChange = { isApex = it })
        }
        if (!isApex) {
            OutlinedTextField(value = prefix, onValueChange = { prefix = it }, label = { Text("Subdomain Prefix") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable health check", modifier = Modifier.weight(1f))
            Switch(checked = healthEnabled, onCheckedChange = { healthEnabled = it })
        }
        if (healthEnabled) {
            OutlinedTextField(
                value = path, onValueChange = { path = it },
                label = { Text("Health Check Path") },
                placeholder = { Text("/") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = rangeStart.toString(), onValueChange = { rangeStart = it.toIntOrNull() ?: rangeStart }, label = { Text("Status Min") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = rangeEnd.toString(), onValueChange = { rangeEnd = it.toIntOrNull() ?: rangeEnd }, label = { Text("Status Max") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Text("Poll Interval", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120).forEach { seconds ->
                    Button(
                        onClick = { interval = seconds }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (interval == seconds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (interval == seconds) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) { Text("${seconds}s", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            val healthPath = if (healthEnabled) path.ifBlank { "/" } else ""
            Button(onClick = { onAdd(if (isApex) "" else prefix, isApex, healthPath, rangeStart, rangeEnd, interval) }, enabled = isApex || prefix.isNotBlank()) {
                Text("Add Subdomain", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

