package dev.tsk.server.watcher.core.domain.repository

import dev.tsk.server.watcher.core.domain.model.HostedZoneSummary

interface IRoute53Repository {
    suspend fun listHostedZones(): Result<List<HostedZoneSummary>>
    suspend fun upsertARecord(hostedZoneId: String, recordName: String, ip: String, ttl: Long = 300): Result<Unit>
}
