package dev.tsk.server.watcher.ui.components

sealed class UiState<T> {
    class Loading<T> : UiState<T>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error<T>(val message: String, val exception: Exception? = null) : UiState<T>()
}
