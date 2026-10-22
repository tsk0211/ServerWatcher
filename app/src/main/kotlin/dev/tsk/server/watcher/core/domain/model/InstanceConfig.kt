package dev.tsk.server.watcher.core.domain.model

data class InstanceConfig(
    val id: String,
    val instanceId: String,
    val key: String,
    val value: String,
)
