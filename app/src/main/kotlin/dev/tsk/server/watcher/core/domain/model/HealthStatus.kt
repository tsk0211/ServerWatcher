package dev.tsk.server.watcher.core.domain.model

sealed class HealthStatus {
    data object Healthy : HealthStatus()
    data object Unhealthy : HealthStatus()
    data object Unknown : HealthStatus()
    data class Error(val message: String) : HealthStatus()

    val displayName: String
        get() = when (this) {
            is Healthy -> "Healthy"
            is Unhealthy -> "Unhealthy"
            is Unknown -> "Unknown"
            is Error -> "Error"
        }
}
