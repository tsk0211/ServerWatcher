package dev.tsk.server.watcher.core.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tsk.server.watcher.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The user's light/dark choice. Not sensitive, so plain preferences rather than the encrypted store. */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(ThemeMode.fromName(prefs.getString(KEY_MODE, null)))
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }

    private companion object {
        const val PREFS_NAME = "ui_prefs"
        const val KEY_MODE = "theme_mode"
    }
}
