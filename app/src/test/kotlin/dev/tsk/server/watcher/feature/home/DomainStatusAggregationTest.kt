package dev.tsk.server.watcher.feature.home

import dev.tsk.server.watcher.core.domain.model.Ec2State.PENDING
import dev.tsk.server.watcher.core.domain.model.Ec2State.RUNNING
import dev.tsk.server.watcher.core.domain.model.Ec2State.SHUTTING_DOWN
import dev.tsk.server.watcher.core.domain.model.Ec2State.STOPPED
import dev.tsk.server.watcher.core.domain.model.Ec2State.STOPPING
import dev.tsk.server.watcher.core.domain.model.Ec2State.TERMINATED
import dev.tsk.server.watcher.core.domain.model.Ec2State.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainStatusAggregationTest {

    @Test
    fun `unreadable states are never reported as stopped`() {
        // Regression: a failed poll (expired token, no network, missing permission) returns UNKNOWN
        // for every instance. That used to be summarised as "All stopped" for a live domain.
        assertEquals(DomainStatus.UNKNOWN, aggregateDomainStatus(listOf(UNKNOWN, UNKNOWN)))
    }

    @Test
    fun `one unreadable instance makes the summary unknown rather than mixed or stopped`() {
        assertEquals(DomainStatus.UNKNOWN, aggregateDomainStatus(listOf(RUNNING, UNKNOWN)))
        assertEquals(DomainStatus.UNKNOWN, aggregateDomainStatus(listOf(STOPPED, UNKNOWN)))
    }

    @Test
    fun `all running`() {
        assertEquals(DomainStatus.ALL_RUNNING, aggregateDomainStatus(listOf(RUNNING, RUNNING)))
    }

    @Test
    fun `stopped and terminated instances count as all stopped`() {
        assertEquals(DomainStatus.ALL_STOPPED, aggregateDomainStatus(listOf(STOPPED, TERMINATED)))
    }

    @Test
    fun `some running is mixed`() {
        assertEquals(DomainStatus.MIXED, aggregateDomainStatus(listOf(RUNNING, STOPPED)))
    }

    @Test
    fun `any instance changing state means transitioning, even if another is unreadable`() {
        assertEquals(DomainStatus.TRANSITIONING, aggregateDomainStatus(listOf(RUNNING, PENDING)))
        assertEquals(DomainStatus.TRANSITIONING, aggregateDomainStatus(listOf(STOPPING, STOPPED)))
        assertEquals(DomainStatus.TRANSITIONING, aggregateDomainStatus(listOf(SHUTTING_DOWN, UNKNOWN)))
    }

    @Test
    fun `no instances is unknown`() {
        assertEquals(DomainStatus.UNKNOWN, aggregateDomainStatus(emptyList()))
    }
}
