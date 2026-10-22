package dev.tsk.server.watcher.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import dev.tsk.server.watcher.ui.theme.MontserratFamily
import dev.tsk.server.watcher.ui.theme.ServerWatcherGreen

@Composable
fun LogoText() {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = "Server",
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )
        Text(
            text = "Watcher",
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                color = ServerWatcherGreen,
            ),
        )
    }
}
