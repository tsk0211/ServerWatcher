package dev.tsk.server.watcher.core.data.health

import dev.tsk.server.watcher.core.domain.model.HealthStatus
import dev.tsk.server.watcher.core.domain.repository.IHealthRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import javax.inject.Inject

class HealthRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
) : IHealthRepository {

    override suspend fun check(
        url: String,
        path: String,
        expectedRangeStart: Int,
        expectedRangeEnd: Int,
    ): HealthStatus = runCatching {
        val response: HttpResponse = httpClient.get("$url$path")
        val status = response.status.value
        if (status in expectedRangeStart..expectedRangeEnd) {
            HealthStatus.Healthy
        } else {
            HealthStatus.Unhealthy
        }
    }.getOrElse { e ->
        HealthStatus.Error(e.message ?: "Unknown error")
    }
}
