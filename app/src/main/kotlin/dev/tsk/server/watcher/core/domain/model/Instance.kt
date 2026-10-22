package dev.tsk.server.watcher.core.domain.model

data class Instance(
    val id: String,
    val instanceId: String,
    val subdomainId: String,
    val label: String,
    val region: String,
    val publicIp: String? = null,
    val ec2State: Ec2State = Ec2State.UNKNOWN,
    val healthStatus: HealthStatus = HealthStatus.Unknown,
    val isPrimary: Boolean = false,
)
