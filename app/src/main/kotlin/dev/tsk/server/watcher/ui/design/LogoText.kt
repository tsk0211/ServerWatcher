package dev.tsk.server.watcher.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import dev.tsk.server.watcher.ui.theme.MontserratFamily

/**
 * "Server" in the text color, "Watcher" in a green-to-teal gradient. Stacked by default; pass
 * [inline] for a single line in top bars.
 */
@Composable
fun LogoText(inline: Boolean = false) {
    val base = MaterialTheme.typography.titleLarge.copy(
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.Bold,
    )
    val serverStyle = base.copy(color = MaterialTheme.colorScheme.onSurface)
    val watcherStyle = base.copy(
        brush = Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
        ),
    )

    if (inline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Server", style = serverStyle)
            Text("Watcher", style = watcherStyle)
        }
    } else {
        Column(horizontalAlignment = Alignment.Start) {
            Text("Server", style = serverStyle)
            Text("Watcher", style = watcherStyle)
        }
    }
}

/** Same wordmark at any size, for the splash screen and the sign-in hero. */
@Composable
fun LogoWordmark(style: TextStyle) {
    val base = style.copy(fontFamily = MontserratFamily, fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Server", style = base.copy(color = MaterialTheme.colorScheme.onBackground))
        Text(
            "Watcher",
            style = base.copy(
                brush = Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                ),
            ),
        )
    }
}
