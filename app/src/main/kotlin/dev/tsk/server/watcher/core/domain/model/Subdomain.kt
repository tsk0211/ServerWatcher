package dev.tsk.server.watcher.core.domain.model

data class Subdomain(
    val id: String,
    val domainId: String,
    val prefix: String,
    val isApex: Boolean,
    val healthPath: String,
    val healthRangeStart: Int,
    val healthRangeEnd: Int,
    val healthIntervalSeconds: Int,
)
