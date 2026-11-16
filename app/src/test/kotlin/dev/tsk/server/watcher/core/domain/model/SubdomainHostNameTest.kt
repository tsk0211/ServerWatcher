package dev.tsk.server.watcher.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SubdomainHostNameTest {

    private val domain = Domain(
        id = "d1",
        name = "Java App",
        region = "us-east-1",
        hostedZoneName = "kulkarni.store",
    )

    private fun subdomain(prefix: String, isApex: Boolean = false) = Subdomain(
        id = "s1",
        domainId = "d1",
        prefix = prefix,
        isApex = isApex,
        healthPath = "/actuator/health",
        healthRangeStart = 200,
        healthRangeEnd = 299,
        healthIntervalSeconds = 30,
    )

    @Test
    fun `prefixed subdomain is joined with the hosted zone`() {
        assertEquals("api.kulkarni.store", subdomain("api").hostName(domain))
    }

    @Test
    fun `apex subdomain resolves to the zone itself`() {
        assertEquals("kulkarni.store", subdomain("", isApex = true).hostName(domain))
    }

    @Test
    fun `falls back to domain name when no hosted zone is linked`() {
        val noZone = domain.copy(name = "example.com", hostedZoneName = null)
        assertEquals("app.example.com", subdomain("app").hostName(noZone))
    }
}
