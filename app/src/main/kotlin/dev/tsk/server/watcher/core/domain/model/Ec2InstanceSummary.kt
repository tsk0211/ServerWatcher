package dev.tsk.server.watcher.core.domain.model

data class Ec2InstanceSummary(
    val instanceId: String,
    val name: String?,
    val stateName: String,
) {
    val displayName: String get() = name ?: instanceId
    val dropdownLabel: String get() = if (name != null) "$name ($instanceId) — $stateName" else "$instanceId — $stateName"
}
