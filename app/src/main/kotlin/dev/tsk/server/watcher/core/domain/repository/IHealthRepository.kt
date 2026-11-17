package dev.tsk.server.watcher.core.domain.repository

import dev.tsk.server.watcher.core.domain.model.HealthCheckOptions
import dev.tsk.server.watcher.core.domain.model.HealthStatus

interface IHealthRepository {
    suspend fun check(
        url: String,
        path: String,
        expectedRangeStart: Int,
        expectedRangeEnd: Int,
        timeoutSeconds: Int = HealthCheckOptions.DEFAULT_TIMEOUT_SECONDS,
    ): HealthStatus
}
