package dev.tsk.server.watcher.core.data.health

import dev.tsk.server.watcher.core.domain.model.HealthStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// runBlocking, not runTest: the mock engine runs on a real dispatcher, so virtual time would skip past
// the repository's withTimeout while the request is still in flight. Retry pauses are real (1 s each).
class HealthRepositoryImplTest {

    private fun repo(vararg statuses: HttpStatusCode, onRequest: () -> Unit = {}): HealthRepositoryImpl {
        var calls = 0
        return HealthRepositoryImpl(
            HttpClient(MockEngine {
                onRequest()
                respond("", statuses[minOf(calls++, statuses.lastIndex)])
            }),
        )
    }

    private suspend fun HealthRepositoryImpl.check() = check("https://api.example.com", "/actuator/health", 200, 299)

    @Test
    fun `status inside range is healthy after one request`() = runBlocking {
        var requests = 0
        assertEquals(HealthStatus.Healthy, repo(HttpStatusCode.OK) { requests++ }.check())
        assertEquals(1, requests)
    }

    @Test
    fun `status outside range reports the code after three attempts`() = runBlocking {
        var requests = 0
        assertEquals(HealthStatus.Unhealthy(404), repo(HttpStatusCode.NotFound) { requests++ }.check())
        assertEquals(3, requests)
    }

    @Test
    fun `a later healthy attempt wins`() = runBlocking {
        var requests = 0
        val result = repo(HttpStatusCode.ServiceUnavailable, HttpStatusCode.OK) { requests++ }.check()
        assertEquals(HealthStatus.Healthy, result)
        assertEquals(2, requests)
    }

    @Test
    fun `transport failure is an error`() = runBlocking {
        val failing = HealthRepositoryImpl(HttpClient(MockEngine { error("boom") }))
        assertTrue(failing.check() is HealthStatus.Error)
    }

    @Test
    fun `request timeout is reported as a short reason`() = runBlocking {
        val timingOut = HealthRepositoryImpl(
            HttpClient(MockEngine { throw HttpRequestTimeoutException("https://api.example.com", 10_000L) }),
        )
        assertEquals(HealthStatus.Error("timeout"), timingOut.check())
    }
}
