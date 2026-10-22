package dev.tsk.server.watcher.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun LoadingDots(
    modifier: Modifier = Modifier,
    dotSize: Dp = 14.dp,
    dotSpacing: Dp = 10.dp,
    bounceHeight: Dp = 18.dp,
) {
    val dotCount = 5
    val animatables = remember { List(dotCount) { Animatable(0f) } }

    animatables.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            delay(index * 100L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        animatables.forEach { animatable ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .offset(y = (-bounceHeight.value * animatable.value).dp)
                    .background(
                        color = MaterialTheme.colorScheme.onBackground,
                        shape = CircleShape,
                    )
            )
        }
    }
}
