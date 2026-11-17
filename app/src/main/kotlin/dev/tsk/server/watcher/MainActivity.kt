package dev.tsk.server.watcher

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import dev.tsk.server.watcher.core.data.local.ThemePreferences
import dev.tsk.server.watcher.core.domain.model.ThemeMode
import dev.tsk.server.watcher.navigation.NavGraph
import dev.tsk.server.watcher.ui.components.AnimatedToast
import dev.tsk.server.watcher.ui.components.ToastManager
import dev.tsk.server.watcher.ui.theme.ServerWatcherTheme
import kotlinx.coroutines.delay
import javax.inject.Inject

private const val TAG = "SW"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var toastManager: ToastManager
    @Inject lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "[Main] onCreate")
        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferences.mode.collectAsState()
            ServerWatcherTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
            ) {
                val currentToast by toastManager.currentToast.collectAsState()

                LaunchedEffect(currentToast) {
                    val toast = currentToast ?: return@LaunchedEffect
                    Log.d(TAG, "[Toast] showing: ${toast.type} — ${toast.message}")
                    delay(toast.duration)
                    toastManager.clearToast()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    NavGraph()
                    AnimatedToast(
                        toast = currentToast,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding(),
                    )
                }
            }
        }
    }
}
