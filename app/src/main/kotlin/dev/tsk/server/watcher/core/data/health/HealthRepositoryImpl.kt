package dev.tsk.server.watcher.core.data.health

import dev.tsk.server.watcher.core.domain.model.HealthStatus
import dev.tsk.server.watcher.core.domain.repository.IHealthRepository
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.net.ssl.SSLException

class HealthRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
) : IHealthRepository {

    /** A check passes if any of [ATTEMPTS] tries does; otherwise the last failure is reported. */
    override suspend fun check(
        url: String,
        path: String,
        expectedRangeStart: Int,
        expectedRangeEnd: Int,
        timeoutSeconds: Int,
    ): HealthStatus {
        var result: HealthStatus = HealthStatus.Unknown
        repeat(ATTEMPTS) { attempt ->
            result = attemptOnce("$url$path", expectedRangeStart..expectedRangeEnd, timeoutSeconds)
            if (result is HealthStatus.Healthy) return result
            if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MILLIS)
        }
        return result
    }

    private suspend fun attemptOnce(url: String, expected: IntRange, timeoutSeconds: Int): HealthStatus = runCatching {
        val timeoutMillis = timeoutSeconds * 1_000L
        val response: HttpResponse = httpClient.get(url) {
            timeout {
                connectTimeoutMillis = timeoutMillis
                requestTimeoutMillis = timeoutMillis
                socketTimeoutMillis = timeoutMillis
            }
        }
        val status = response.status.value
        if (status in expected) HealthStatus.Healthy else HealthStatus.Unhealthy(status)
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

    private companion object {
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MILLIS = 1_000L
    }
}
