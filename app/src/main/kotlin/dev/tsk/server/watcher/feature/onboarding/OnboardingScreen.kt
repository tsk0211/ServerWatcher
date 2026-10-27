package dev.tsk.server.watcher.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tsk.server.watcher.ui.design.AppBackground
import dev.tsk.server.watcher.ui.design.IconBadge
import dev.tsk.server.watcher.ui.design.LogoText
import dev.tsk.server.watcher.ui.design.SwButton
import dev.tsk.server.watcher.ui.design.SwCard
import dev.tsk.server.watcher.ui.design.SwTextField
import dev.tsk.server.watcher.ui.design.transparentTopBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var userPoolId by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var identityPoolId by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("us-east-1") }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onComplete() }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val canSave = userPoolId.isNotBlank() && clientId.isNotBlank() &&
        identityPoolId.isNotBlank() && region.isNotBlank()

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = { TopAppBar(colors = transparentTopBarColors(), title = { LogoText(inline = true) }) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                IconBadge(Icons.Default.Cloud, MaterialTheme.colorScheme.primary, size = 60.dp)
                Text(
                    "Connect your AWS account",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Enter the four values from your CDK deploy output. They stay on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))

                SwCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SwTextField(
                            value = userPoolId,
                            onValueChange = { userPoolId = it },
                            label = { Text("User Pool ID") },
                            placeholder = { Text("us-east-1_xxxxxxxx") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        SwTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text("App Client ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        SwTextField(
                            value = identityPoolId,
                            onValueChange = { identityPoolId = it },
                            label = { Text("Identity Pool ID") },
                            placeholder = { Text("us-east-1:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        SwTextField(
                            value = region,
                            onValueChange = { region = it },
                            label = { Text("AWS Region") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }

                SwButton(
                    text = "Save & continue",
                    onClick = {
                        viewModel.save(
                            userPoolId = userPoolId,
                            clientId = clientId,
                            identityPoolId = identityPoolId,
                            region = region,
                        )
                    },
                    enabled = canSave,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
