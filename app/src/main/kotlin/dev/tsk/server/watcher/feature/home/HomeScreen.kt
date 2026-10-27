package dev.tsk.server.watcher.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tsk.server.watcher.R
import dev.tsk.server.watcher.core.domain.model.HostedZoneSummary
import dev.tsk.server.watcher.ui.components.ConfirmationDialog
import dev.tsk.server.watcher.ui.components.ErrorState
import dev.tsk.server.watcher.ui.components.UiState
import dev.tsk.server.watcher.ui.design.AppBackground
import dev.tsk.server.watcher.ui.design.IconBadge
import dev.tsk.server.watcher.ui.design.LogoText
import dev.tsk.server.watcher.ui.design.SectionLabel
import dev.tsk.server.watcher.ui.design.SheetHeader
import dev.tsk.server.watcher.ui.design.SkeletonList
import dev.tsk.server.watcher.ui.design.StatTile
import dev.tsk.server.watcher.ui.design.StatusOrb
import dev.tsk.server.watcher.ui.design.SwButton
import dev.tsk.server.watcher.ui.design.SwCard
import dev.tsk.server.watcher.ui.design.SwTextField
import dev.tsk.server.watcher.ui.design.readable
import dev.tsk.server.watcher.ui.design.transparentTopBarColors
import dev.tsk.server.watcher.ui.theme.StatePending
import dev.tsk.server.watcher.ui.theme.StateRunning
import dev.tsk.server.watcher.ui.theme.StateStopped
import dev.tsk.server.watcher.ui.theme.StateUnknown
import dev.tsk.server.watcher.ui.theme.sw
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

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    colors = transparentTopBarColors(),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.mipmap.ic_launcher_round),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                            )
                            Spacer(Modifier.width(10.dp))
                            LogoText(inline = true)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAddSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add domain", fontWeight = FontWeight.SemiBold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.large,
                )
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
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (state.data.isEmpty()) {
                            item { EmptyDomains(onAdd = { showAddSheet = true }) }
                        } else {
                            item { Overview(state.data) }
                            item { SectionLabel("Your domains", Modifier.padding(start = 4.dp, top = 6.dp)) }
                            items(state.data, key = { it.domainId }) { domain ->
                                DomainCard(
                                    domain = domain,
                                    onToggle = { newState -> pendingToggle = domain.domainId to newState },
                                    onClick = { onDomainClick(domain.domainId) },
                                )
                            }
                        }
                        item { Spacer(Modifier.height(96.dp)) }
                    }
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

/** Three headline numbers across all domains, so the state of everything is visible before scrolling. */
@Composable
private fun Overview(domains: List<DomainUiModel>) {
    val sw = MaterialTheme.sw
    val loaded = domains.filter { it.statusLoaded }
    val up = loaded.sumOf { it.runningCount }
    val total = loaded.sumOf { it.instanceCount }
    val attention = loaded.count {
        it.status == DomainStatus.MIXED || it.status == DomainStatus.TRANSITIONING || it.status == DomainStatus.UNKNOWN
    }
    val allLoaded = loaded.size == domains.size

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("${domains.size}", "Domains", sw.info, Modifier.weight(1f))
        StatTile(if (allLoaded) "$up/$total" else "…", "Instances up", sw.success, Modifier.weight(1f))
        StatTile(
            value = if (allLoaded) "$attention" else "…",
            label = "Need attention",
            color = if (allLoaded && attention > 0) sw.warning else StateStopped,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EmptyDomains(onAdd: () -> Unit) {
    SwCard(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), accent = MaterialTheme.colorScheme.primary) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(Icons.Default.Language, MaterialTheme.colorScheme.primary, size = 64.dp)
            Text("No domains yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Add a domain, then group the EC2 instances behind it into subdomains. One switch will start or stop the whole environment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            SwButton(text = "Add your first domain", onClick = onAdd, icon = Icons.Default.Add, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DomainCard(
    domain: DomainUiModel,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val color = statusColor(domain.status)
    // A stopped or unreadable domain stays neutral; only live or changing ones glow.
    val glows = domain.statusLoaded &&
        (domain.status == DomainStatus.ALL_RUNNING || domain.status == DomainStatus.MIXED || domain.status == DomainStatus.TRANSITIONING)

    SwCard(
        modifier = Modifier.fillMaxWidth(),
        accent = if (glows) color else null,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusOrb(
                    color = color,
                    size = 12.dp,
                    pulse = domain.statusLoaded && (domain.status == DomainStatus.ALL_RUNNING || domain.status == DomainStatus.TRANSITIONING),
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        domain.domainName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = statusLabel(domain),
                        style = MaterialTheme.typography.labelMedium,
                        color = color.readable(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Switch(
                    checked = domain.isMasterOn,
                    onCheckedChange = onToggle,
                    // Don't let the user flip a switch whose state we haven't confirmed yet.
                    enabled = domain.statusLoaded && domain.status != DomainStatus.TRANSITIONING,
                )
            }
            if (domain.statusLoaded && domain.instanceCount > 0) {
                InstanceMeter(running = domain.runningCount, total = domain.instanceCount, color = color, region = domain.region)
            }
        }
    }
}

/** Thin bar showing how many of a domain's instances are up, with the region on the right. */
@Composable
private fun InstanceMeter(running: Int, total: Int, color: Color, region: String) {
    val fraction by animateFloatAsState(
        targetValue = running.toFloat() / total,
        animationSpec = tween(600),
        label = "meter",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            text = "$running/$total up · $region",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun statusColor(status: DomainStatus): Color = when (status) {
    DomainStatus.ALL_RUNNING   -> StateRunning
    // Stopped is a deliberate state, not an error, so it is neutral rather than red.
    DomainStatus.ALL_STOPPED   -> StateStopped
    DomainStatus.TRANSITIONING -> StatePending
    DomainStatus.MIXED         -> StatePending
    DomainStatus.UNKNOWN       -> StateUnknown
}

private fun statusLabel(domain: DomainUiModel): String = when {
    !domain.statusLoaded                         -> "Checking…"
    domain.instanceCount == 0                    -> "No instances"
    domain.status == DomainStatus.ALL_RUNNING    -> "All running (${domain.runningCount})"
    domain.status == DomainStatus.ALL_STOPPED    -> "All stopped"
    domain.status == DomainStatus.TRANSITIONING  -> "Transitioning…"
    domain.status == DomainStatus.MIXED          -> "${domain.runningCount}/${domain.instanceCount} running"
    else -> "Status unavailable"
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
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SheetHeader(
            icon = Icons.Default.Language,
            title = "Add domain",
            subtitle = "Instances are grouped under a domain, then subdomains",
        )
        SwTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Domain Name") }, placeholder = { Text("e.g. mauli.store") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        SwTextField(
            value = region, onValueChange = { region = it },
            label = { Text("AWS Region") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
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
            SwTextField(
                value = manualZoneId, onValueChange = { manualZoneId = it },
                label = { Text("Hosted Zone ID (optional)") },
                placeholder = { Text("Z0000000000000000000") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        val effectiveZoneId = if (hostedZones.isNotEmpty()) selectedZone?.id else manualZoneId.ifBlank { null }
        val effectiveZoneName = if (hostedZones.isNotEmpty()) selectedZone?.name else null
        val canAdd = name.isNotBlank() && region.isNotBlank()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            SwButton(
                text = "Add domain",
                onClick = { if (canAdd) onAdd(name, region, effectiveZoneId, effectiveZoneName) },
                enabled = canAdd,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
