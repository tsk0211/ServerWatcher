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

class HealthRepositoryImplTest {

    private fun repo(status: HttpStatusCode) =
        HealthRepositoryImpl(HttpClient(MockEngine { respond("", status) }))

    private fun check(repo: HealthRepositoryImpl) =
        runBlocking { repo.check("https://api.example.com", "/actuator/health", 200, 299) }

    @Test
    fun `status inside range is healthy`() {
        assertEquals(HealthStatus.Healthy, check(repo(HttpStatusCode.OK)))
    }

    @Test
    fun `status outside range reports the code`() {
        assertEquals(HealthStatus.Unhealthy(404), check(repo(HttpStatusCode.NotFound)))
    }

    @Test
    fun `transport failure is an error`() {
        val failing = HealthRepositoryImpl(HttpClient(MockEngine { error("boom") }))
        assertTrue(check(failing) is HealthStatus.Error)
    }

    @Test
    fun `request timeout is reported as a short reason`() {
        val timingOut = HealthRepositoryImpl(
            HttpClient(MockEngine { throw HttpRequestTimeoutException("https://api.example.com", 10_000L) }),
        )
        assertEquals(HealthStatus.Error("timeout"), check(timingOut))
    }
}
