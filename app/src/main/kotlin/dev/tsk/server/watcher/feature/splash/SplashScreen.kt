package dev.tsk.server.watcher.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tsk.server.watcher.R
import dev.tsk.server.watcher.ui.theme.MontserratFamily
import dev.tsk.server.watcher.ui.theme.ServerWatcherGreen
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onReady: () -> Unit) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(400))
        scale.animateTo(1f, animationSpec = tween(500, easing = EaseOutBack))
        delay(600)
        onReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value),
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_round),
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Server",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
            )
            Text(
                text = "Watcher",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    color = ServerWatcherGreen,
                ),
            )
        }
    }
}
