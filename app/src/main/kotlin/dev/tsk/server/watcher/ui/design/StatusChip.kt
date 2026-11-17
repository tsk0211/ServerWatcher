package dev.tsk.server.watcher.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tsk.server.watcher.core.domain.model.Ec2State
import dev.tsk.server.watcher.core.domain.model.HealthStatus
import dev.tsk.server.watcher.ui.theme.StateError
import dev.tsk.server.watcher.ui.theme.StatePending
import dev.tsk.server.watcher.ui.theme.StateRunning
import dev.tsk.server.watcher.ui.theme.StateStopped
import dev.tsk.server.watcher.ui.theme.StateUnknown
import dev.tsk.server.watcher.ui.theme.sw

@Composable
fun Ec2StateChip(state: Ec2State, modifier: Modifier = Modifier) {
    val color = when (state) {
        Ec2State.RUNNING -> StateRunning
        Ec2State.STOPPED -> StateStopped
        Ec2State.PENDING, Ec2State.STOPPING, Ec2State.SHUTTING_DOWN -> StatePending
        Ec2State.TERMINATED -> StateError
        Ec2State.UNKNOWN -> StateUnknown
    }
    val icon = when (state) {
        Ec2State.RUNNING -> Icons.Default.PlayArrow
        Ec2State.STOPPED -> Icons.Default.Stop
        Ec2State.PENDING, Ec2State.STOPPING, Ec2State.SHUTTING_DOWN -> Icons.Default.Schedule
        Ec2State.TERMINATED -> Icons.Default.Cancel
        Ec2State.UNKNOWN -> Icons.Default.Info
    }
    StatusChip(
        label = state.displayName,
        dotColor = color,
        pulse = state == Ec2State.RUNNING || state.isTransitional,
        icon = icon,
        modifier = modifier,
    )
}

private const val MAX_ERROR_LABEL = 24

@Composable
fun HealthStatusChip(status: HealthStatus, modifier: Modifier = Modifier) {
    // The icon repeats the state, so it still reads in glare or for colour-blind users.
    val (label, color, icon) = when (status) {
        is HealthStatus.Healthy -> Triple("Healthy", StateRunning, Icons.Default.CheckCircle)
        is HealthStatus.Unhealthy -> Triple("Unhealthy · HTTP ${status.statusCode}", StateError, Icons.Default.Warning)
        is HealthStatus.Unknown -> Triple("Unknown", StateUnknown, Icons.Default.Info)
        is HealthStatus.Error -> Triple("Error · ${status.message.take(MAX_ERROR_LABEL)}", StateError, Icons.Default.Error)
    }
    StatusChip(label = label, dotColor = color, icon = icon, modifier = modifier)
}

/** Tinted pill: a status orb and a label, both in the state's color (darkened on light themes). */
@Composable
fun StatusChip(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
    icon: ImageVector? = null,
) {
    val dark = MaterialTheme.sw.isDark
    val animatedColor by animateColorAsState(
        targetValue = dotColor,
        animationSpec = tween(400),
        label = "chipColor",
    )
    val textColor = animatedColor.readable()

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(animatedColor.copy(alpha = if (dark) 0.14f else 0.20f))
            .border(1.dp, animatedColor.copy(alpha = if (dark) 0.30f else 0.55f), CircleShape)
            .padding(start = if (icon != null) 8.dp else 2.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val pulseAlpha by rememberInfiniteTransition(label = "chipPulse").animateFloat(
                initialValue = 1f,
                targetValue = if (pulse) 0.5f else 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "chipPulseAlpha",
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp).alpha(pulseAlpha),
            )
        } else {
            StatusOrb(color = animatedColor, size = 6.dp, pulse = pulse)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(start = if (icon != null) 4.dp else 1.dp, top = 4.dp, bottom = 4.dp),
        )
    }
}
