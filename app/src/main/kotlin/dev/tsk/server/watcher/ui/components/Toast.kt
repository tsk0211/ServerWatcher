package dev.tsk.server.watcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    val backgroundColor = when (toast.type) {
        ToastType.SUCCESS -> Color(0xFF0D2B0D)
        ToastType.ERROR   -> Color(0xFF2B0D0D)
        ToastType.WARNING -> Color(0xFF2B1A0D)
        ToastType.INFO    -> Color(0xFF0D0D0D)
    }
    val textColor = when (toast.type) {
        ToastType.SUCCESS -> Color(0xFF69F0AE)
        ToastType.ERROR   -> Color(0xFFFF6E6E)
        ToastType.WARNING -> Color(0xFFFFD180)
        ToastType.INFO    -> Color(0xFF1E6FFF)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = toast.message,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

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
