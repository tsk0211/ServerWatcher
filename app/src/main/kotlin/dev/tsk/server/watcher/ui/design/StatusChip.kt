package dev.tsk.server.watcher.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.tsk.server.watcher.core.domain.model.Ec2State
import dev.tsk.server.watcher.core.domain.model.HealthStatus
import dev.tsk.server.watcher.ui.theme.StateError
import dev.tsk.server.watcher.ui.theme.StatePending
import dev.tsk.server.watcher.ui.theme.StateRunning
import dev.tsk.server.watcher.ui.theme.StateStopped
import dev.tsk.server.watcher.ui.theme.StateUnknown

@Composable
fun Ec2StateChip(state: Ec2State) {
    val color = when (state) {
        Ec2State.RUNNING -> StateRunning
        Ec2State.STOPPED -> StateStopped
        Ec2State.PENDING, Ec2State.STOPPING, Ec2State.SHUTTING_DOWN -> StatePending
        Ec2State.TERMINATED -> StateError
        Ec2State.UNKNOWN -> StateUnknown
    }
    StatusChip(label = state.displayName, dotColor = color)
}

@Composable
fun HealthStatusChip(status: HealthStatus) {
    val (label, color) = when (status) {
        is HealthStatus.Healthy -> "Healthy" to StateRunning
        is HealthStatus.Unhealthy -> "Unhealthy" to StateError
        is HealthStatus.Unknown -> "Unknown" to StateUnknown
        is HealthStatus.Error -> "Error" to StateError
    }
    StatusChip(label = label, dotColor = color)
}

@Composable
fun StatusChip(label: String, dotColor: Color) {
    val animatedColor by animateColorAsState(
        targetValue = dotColor,
        animationSpec = tween(400),
        label = "chipColor",
    )
    SuggestionChip(
        onClick = {},
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape),
                ) {
                    drawCircle(color = animatedColor)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = animatedColor.copy(alpha = 0.12f),
        ),
    )
}
