package dev.tsk.server.watcher.core.domain.usecase

import dev.tsk.server.watcher.core.domain.model.Domain
import dev.tsk.server.watcher.core.domain.model.Instance
import dev.tsk.server.watcher.core.domain.repository.IDomainRepository
import dev.tsk.server.watcher.core.domain.repository.IInstanceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUseCasesTest {

    private val domain = Domain(id = "d1", name = "example.com", region = "us-east-1", isMasterOn = false)
    private val usEast = Instance(id = "r1", instanceId = "i-1", subdomainId = "s1", label = "web", region = "us-east-1")
    private val euWest = Instance(id = "r2", instanceId = "i-2", subdomainId = "s1", label = "api", region = "eu-west-1")

    private val domainRepository = mockk<IDomainRepository>(relaxed = true)
    private val instanceRepository = mockk<IInstanceRepository>()
    private val startInstance = mockk<StartInstanceUseCase>()
    private val stopInstance = mockk<StopInstanceUseCase>()

    private val startDomain = StartDomainUseCase(domainRepository, instanceRepository, startInstance)
    private val stopDomain = StopDomainUseCase(domainRepository, instanceRepository, stopInstance)

    private fun givenDomainWith(vararg instances: Instance) {
        coEvery { domainRepository.getById("d1") } returns domain
        coEvery { instanceRepository.getAllByDomain("d1") } returns instances.toList()
    }

    @Test
    fun `start uses each instance's own region, not the domain's`() = runTest {
        givenDomainWith(usEast, euWest)
        coEvery { startInstance(any(), any()) } returns Result.success(Unit)

        val result = startDomain("d1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { startInstance("i-1", "us-east-1") }
        coVerify(exactly = 1) { startInstance("i-2", "eu-west-1") }
        coVerify(exactly = 0) { startInstance("i-2", "us-east-1") }
        coVerify { domainRepository.update(domain.copy(isMasterOn = true)) }
    }

    @Test
    fun `stop uses each instance's own region, not the domain's`() = runTest {
        givenDomainWith(usEast, euWest)
        coEvery { stopInstance(any(), any()) } returns Result.success(Unit)

        val result = stopDomain("d1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { stopInstance("i-2", "eu-west-1") }
        coVerify { domainRepository.update(domain.copy(isMasterOn = false)) }
    }

    @Test
    fun `one failing instance does not stop the others from starting`() = runTest {
        givenDomainWith(usEast, euWest)
        coEvery { startInstance("i-1", "us-east-1") } returns Result.failure(IllegalStateException("boom"))
        coEvery { startInstance("i-2", "eu-west-1") } returns Result.success(Unit)

        val result = startDomain("d1")

        assertTrue(result.isFailure)
        // The healthy instance was still started...
        coVerify(exactly = 1) { startInstance("i-2", "eu-west-1") }
        // ...but the domain is not marked on, and the error names what failed.
        coVerify(exactly = 0) { domainRepository.update(any()) }
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, "1 of 2" in message && "web" in message && "boom" in message)
    }

    @Test
    fun `a domain with no instances still flips the master switch`() = runTest {
        givenDomainWith()

        val result = startDomain("d1")

        assertTrue(result.isSuccess)
        coVerify { domainRepository.update(domain.copy(isMasterOn = true)) }
    }

    @Test
    fun `missing domain is reported as a failure`() = runTest {
        coEvery { domainRepository.getById("nope") } returns null

        val result = stopDomain("nope")

        assertEquals("Domain not found", result.exceptionOrNull()?.message)
    }
}
