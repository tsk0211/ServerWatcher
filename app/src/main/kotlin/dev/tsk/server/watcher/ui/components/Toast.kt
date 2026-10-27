package dev.tsk.server.watcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.tsk.server.watcher.ui.theme.sw
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ToastType { INFO, SUCCESS, ERROR, WARNING }

data class ToastMessage(
    val message: String,
    val type: ToastType = ToastType.INFO,
    val duration: Long = 3000L,
)

@Singleton
class ToastManager @Inject constructor() {
    private val _currentToast = MutableStateFlow<ToastMessage?>(null)
    val currentToast: StateFlow<ToastMessage?> = _currentToast.asStateFlow()

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        _currentToast.value = ToastMessage(message, type)
    }

    fun clearToast() {
        _currentToast.value = null
    }
}

@Composable
fun ToastView(toast: ToastMessage, modifier: Modifier = Modifier) {
    val sw = MaterialTheme.sw
    val (container, accent, onContainer, icon) = when (toast.type) {
        ToastType.SUCCESS -> ToastStyle(sw.successContainer, sw.success, sw.onSuccessContainer, Icons.Default.CheckCircle)
        ToastType.ERROR   -> ToastStyle(sw.dangerContainer, sw.danger, sw.onDangerContainer, Icons.Default.ErrorOutline)
        ToastType.WARNING -> ToastStyle(sw.warningContainer, sw.warning, sw.onWarningContainer, Icons.Default.WarningAmber)
        ToastType.INFO    -> ToastStyle(sw.infoContainer, sw.info, sw.onInfoContainer, Icons.Default.Info)
    }
    val shape = MaterialTheme.shapes.medium

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(12.dp, shape, ambientColor = accent.copy(alpha = 0.5f), spotColor = accent.copy(alpha = 0.5f))
            .clip(shape)
            .background(container)
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Text(
            text = toast.message,
            color = onContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private data class ToastStyle(val container: Color, val accent: Color, val onContainer: Color, val icon: ImageVector)

@Composable
fun AnimatedToast(toast: ToastMessage?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = toast != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        toast?.let { ToastView(it) }
    }
}
