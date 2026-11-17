package dev.tsk.server.watcher.core.data.health

import dev.tsk.server.watcher.core.domain.model.HealthStatus
import dev.tsk.server.watcher.core.domain.repository.IHealthRepository
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.net.ssl.SSLException

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
            HealthStatus.Unhealthy(status)
        }
    }.getOrElse { e ->
        // Only a cancelled caller aborts; a timeout inside the request is a normal Error result.
        currentCoroutineContext().ensureActive()
        HealthStatus.Error(e.reason())
    }

    private fun Throwable.reason(): String = when (this) {
        is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> "timeout"
        is UnknownHostException -> "DNS lookup failed"
        is SSLException -> "TLS error"
        is ConnectException -> "connection refused"
        else -> this::class.java.simpleName
    }
}
