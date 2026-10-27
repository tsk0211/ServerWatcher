package dev.tsk.server.watcher.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tsk.server.watcher.core.domain.model.Ec2State
import dev.tsk.server.watcher.core.domain.model.HealthStatus
import dev.tsk.server.watcher.ui.theme.StateError
import dev.tsk.server.watcher.ui.theme.StatePending
import dev.tsk.server.watcher.ui.theme.StateRunning
import dev.tsk.server.watcher.ui.theme.StateStopped
import dev.tsk.server.watcher.ui.theme.StateUnknown

@Composable
fun Ec2StateChip(state: Ec2State, modifier: Modifier = Modifier) {
    val color = when (state) {
        Ec2State.RUNNING -> StateRunning
        Ec2State.STOPPED -> StateStopped
        Ec2State.PENDING, Ec2State.STOPPING, Ec2State.SHUTTING_DOWN -> StatePending
        Ec2State.TERMINATED -> StateError
        Ec2State.UNKNOWN -> StateUnknown
    }
    StatusChip(
        label = state.displayName,
        dotColor = color,
        pulse = state == Ec2State.RUNNING || state.isTransitional,
        modifier = modifier,
    )
}

@Composable
fun HealthStatusChip(status: HealthStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        is HealthStatus.Healthy -> "Healthy" to StateRunning
        is HealthStatus.Unhealthy -> "Unhealthy" to StateError
        is HealthStatus.Unknown -> "Unknown" to StateUnknown
        is HealthStatus.Error -> "Error" to StateError
    }
    StatusChip(label = label, dotColor = color, modifier = modifier)
}

/** Tinted pill: a status orb and a label, both in the state's color (darkened on light themes). */
@Composable
fun StatusChip(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
) {
    val animatedColor by animateColorAsState(
        targetValue = dotColor,
        animationSpec = tween(400),
        label = "chipColor",
    )
    val textColor = animatedColor.readable()

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(animatedColor.copy(alpha = 0.14f))
            .border(1.dp, animatedColor.copy(alpha = 0.30f), CircleShape)
            .padding(start = 2.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusOrb(color = animatedColor, size = 6.dp, pulse = pulse)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(start = 1.dp, top = 4.dp, bottom = 4.dp),
        )
    }
}
