package dev.tsk.server.watcher.core.domain.model

data class Domain(
    val id: String,
    val name: String,
    val region: String,
    val isMasterOn: Boolean = false,
    val hostedZoneId: String? = null,
    val hostedZoneName: String? = null,
)
