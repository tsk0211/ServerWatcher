package dev.tsk.server.watcher.feature.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.tsk.server.watcher.R
import dev.tsk.server.watcher.ui.design.AppBackground
import dev.tsk.server.watcher.ui.design.LoadingDots
import dev.tsk.server.watcher.ui.design.LogoWordmark
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onReady: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2_400)
        onReady()
    }

    AppBackground {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            Image(
                painter = painterResource(R.mipmap.ic_launcher_round),
                contentDescription = null,
                modifier = Modifier
                    .size(104.dp)
                    .shadow(32.dp, CircleShape, ambientColor = glow, spotColor = glow)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), CircleShape),
            )
            Spacer(Modifier.height(24.dp))
            LogoWordmark(MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(48.dp))
            LoadingDots()
        }
    }
}
