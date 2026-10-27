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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.tsk.server.watcher.ui.design.AppBackground
import dev.tsk.server.watcher.ui.design.HealthStatusChip
import dev.tsk.server.watcher.ui.design.IconBadge
import dev.tsk.server.watcher.ui.design.SheetHeader
import dev.tsk.server.watcher.ui.design.SkeletonList
import dev.tsk.server.watcher.ui.design.StatusOrb
import dev.tsk.server.watcher.ui.design.SwButton
import dev.tsk.server.watcher.ui.design.SwCard
import dev.tsk.server.watcher.ui.design.SwTextField
import dev.tsk.server.watcher.ui.design.TonalIconButton
import dev.tsk.server.watcher.ui.design.readable
import dev.tsk.server.watcher.ui.design.transparentTopBarColors
import dev.tsk.server.watcher.ui.theme.StatePending
import dev.tsk.server.watcher.ui.theme.StateRunning
import dev.tsk.server.watcher.ui.theme.StateStopped
import dev.tsk.server.watcher.ui.theme.StateUnknown
import dev.tsk.server.watcher.ui.theme.sw
import kotlinx.coroutines.launch

/** Glow color for a subdomain card: red if any instance fails its health check, green if one passes. */
@Composable
private fun subdomainAccent(instances: List<InstanceRowUiModel>): Color? {
    val sw = MaterialTheme.sw
    return when {
        instances.any { it.healthStatus is HealthStatus.Unhealthy || it.healthStatus is HealthStatus.Error } -> sw.danger
        instances.any { it.healthStatus is HealthStatus.Healthy } -> sw.success
        else -> null
    }
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

    AppBackground {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                colors = transparentTopBarColors(),
                title = { Text(screenTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TonalIconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        TonalIconButton(onClick = { showDomainMenu = true }) {
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
                ExtendedFloatingActionButton(
                    onClick = { showAddSubdomainSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add subdomain", fontWeight = FontWeight.SemiBold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large,
                )
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
                is UiState.Loading -> SkeletonList(
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

                is UiState.Success -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { DomainSummary(state.data) }
                    if (state.data.sections.isEmpty()) {
                        item { EmptySubdomains(onAdd = { showAddSubdomainSheet = true }) }
                    }
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
                    item { Spacer(Modifier.height(96.dp)) }
                }
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

/** Hero card at the top of the screen: how much of this domain is up, plus its region and DNS zone. */
@Composable
private fun DomainSummary(data: DomainDetailData) {
    val instances = data.sections.flatMap { it.instances }
    val running = instances.count { it.ec2State == Ec2State.RUNNING }
    val total = instances.size
    val transitioning = instances.any { it.ec2State.isTransitional }
    val sw = MaterialTheme.sw

    val (color, headline) = when {
        !data.isStatusLoaded -> StateUnknown to "Checking status…"
        total == 0 -> StateStopped to "No instances yet"
        transitioning -> StatePending to "Changing state…"
        running == total -> StateRunning to "All $total running"
        running == 0 -> StateStopped to "All $total stopped"
        else -> StatePending to "$running of $total running"
    }
    val live = data.isStatusLoaded && running > 0

    SwCard(modifier = Modifier.fillMaxWidth(), accent = if (live || transitioning) color else null) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusOrb(color = color, size = 12.dp, pulse = data.isStatusLoaded && (running == total && total > 0 || transitioning))
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${data.sections.size} ${if (data.sections.size == 1) "subdomain" else "subdomains"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(Icons.Default.Public, data.domainRegion.ifBlank { "No region" }, sw.info)
                InfoPill(
                    Icons.Default.Dns,
                    data.hostedZoneName ?: "No DNS zone",
                    if (data.hostedZoneName != null) sw.success else StateStopped,
                )
            }
        }
    }
}

@Composable
private fun InfoPill(icon: ImageVector, text: String, tint: Color) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint.readable(), modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint.readable(), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptySubdomains(onAdd: () -> Unit) {
    SwCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBadge(Icons.Default.Dns, MaterialTheme.colorScheme.primary, size = 56.dp)
            Text("No subdomains yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "A subdomain points at one or more EC2 instances and can run a health check against them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            SwButton(text = "Add subdomain", onClick = onAdd, icon = Icons.Default.Add, modifier = Modifier.fillMaxWidth())
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
    val hasHealthCheck = subdomain.healthPath.isNotBlank()

    SwCard(modifier = Modifier.fillMaxWidth(), accent = subdomainAccent(section.instances)) {
        var showSubdomainMenu by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = if (subdomain.isApex) Icons.Default.Public else Icons.Default.Language,
                tint = MaterialTheme.colorScheme.secondary,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    urlDisplay,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (hasHealthCheck) {
                        "${subdomain.healthPath} · ${subdomain.healthRangeStart}–${subdomain.healthRangeEnd} · every ${subdomain.healthIntervalSeconds}s"
                    } else {
                        "No health check"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showSubdomainMenu = false
                            onEditSubdomain()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Subdomain", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showSubdomainMenu = false
                            onDeleteSubdomain()
                        },
                    )
                }
            }
        }

        section.instances.forEach { instance ->
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            InstanceRow(
                instance = instance,
                isStatusLoaded = isStatusLoaded,
                showHealth = hasHealthCheck,
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
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(18.dp))
            Text("Add EC2 instance", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InstanceRow(
    instance: InstanceRowUiModel,
    isStatusLoaded: Boolean,
    showHealth: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit,
    onSetPrimary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    instance.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (instance.isPrimary) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Primary",
                        modifier = Modifier.padding(start = 6.dp).size(15.dp),
                        tint = MaterialTheme.sw.star,
                    )
                }
            }
            Text(
                instance.instanceId,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isStatusLoaded && instance.ec2State == Ec2State.UNKNOWN) {
                    ShimmerChip()
                } else {
                    Ec2StateChip(instance.ec2State)
                    if (showHealth && instance.ec2State == Ec2State.RUNNING && instance.healthStatus !is HealthStatus.Unknown) {
                        HealthStatusChip(instance.healthStatus)
                    }
                }
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
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        showInstanceMenu = false
                        onEdit()
                    },
                )
                if (!instance.isPrimary) {
                    DropdownMenuItem(
                        text = { Text("Set as Primary") },
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                        onClick = {
                            showInstanceMenu = false
                            onSetPrimary()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete Instance", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showInstanceMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** Segmented picker for the health-check poll interval. */
@Composable
private fun PollIntervalPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Poll interval", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(15, 30, 60, 120).forEach { seconds ->
                val isSelected = selected == seconds
                Surface(
                    onClick = { onSelect(seconds) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    ),
                ) {
                    Text(
                        "${seconds}s",
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                SwTextField(
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
                        SwTextField(
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
                    SwTextField(
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
                    SwTextField(
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
                    SwTextField(
                        value = healthPath,
                        onValueChange = { healthPath = it },
                        label = { Text("Health Check Path") },
                        placeholder = { Text("/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwTextField(
                            value = rangeStart.toString(),
                            onValueChange = { rangeStart = it.toIntOrNull() ?: rangeStart },
                            label = { Text("Status Min") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        SwTextField(
                            value = rangeEnd.toString(),
                            onValueChange = { rangeEnd = it.toIntOrNull() ?: rangeEnd },
                            label = { Text("Status Max") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    PollIntervalPicker(selected = interval, onSelect = { interval = it })
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
        SheetHeader(icon = Icons.Default.Dns, title = "Add EC2 instance", subtitle = "Pick a server from your account to manage")

        SwTextField(
            value = region, onValueChange = { region = it.trim() },
            label = { Text("AWS Region") }, placeholder = { Text("us-east-1") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )

        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { if (!instancesLoading) dropdownExpanded = it },
        ) {
            SwTextField(
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

        SwTextField(
            value = label, onValueChange = { label = it },
            label = { Text("Label (friendly name)") }, placeholder = { Text("e.g. Web Server 1") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            SwButton(
                text = "Add instance",
                onClick = {
                    val id = selectedInstance?.instanceId ?: return@SwButton
                    onAdd(id, label.ifBlank { selectedInstance?.displayName ?: id }, region)
                },
                enabled = selectedInstance != null && region.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
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
        SheetHeader(icon = Icons.Default.Language, title = "Add subdomain", subtitle = "Point a name at your instances")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apex domain", modifier = Modifier.weight(1f))
            Switch(checked = isApex, onCheckedChange = { isApex = it })
        }
        if (!isApex) {
            SwTextField(value = prefix, onValueChange = { prefix = it }, label = { Text("Subdomain Prefix") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable health check", modifier = Modifier.weight(1f))
            Switch(checked = healthEnabled, onCheckedChange = { healthEnabled = it })
        }
        if (healthEnabled) {
            SwTextField(
                value = path, onValueChange = { path = it },
                label = { Text("Health Check Path") },
                placeholder = { Text("/") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwTextField(value = rangeStart.toString(), onValueChange = { rangeStart = it.toIntOrNull() ?: rangeStart }, label = { Text("Status Min") }, modifier = Modifier.weight(1f), singleLine = true)
                SwTextField(value = rangeEnd.toString(), onValueChange = { rangeEnd = it.toIntOrNull() ?: rangeEnd }, label = { Text("Status Max") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            PollIntervalPicker(selected = interval, onSelect = { interval = it })
        }
        val healthPath = if (healthEnabled) path.ifBlank { "/" } else ""
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            SwButton(
                text = "Add subdomain",
                onClick = { onAdd(if (isApex) "" else prefix, isApex, healthPath, rangeStart, rangeEnd, interval) },
                enabled = isApex || prefix.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

