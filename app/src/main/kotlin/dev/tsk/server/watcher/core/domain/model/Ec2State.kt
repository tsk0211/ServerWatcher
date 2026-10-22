package dev.tsk.server.watcher.core.domain.model

enum class Ec2State(val displayName: String) {
    PENDING("Pending"),
    RUNNING("Running"),
    SHUTTING_DOWN("Shutting Down"),
    TERMINATED("Terminated"),
    STOPPING("Stopping"),
    STOPPED("Stopped"),
    UNKNOWN("Unknown");

    val isStable: Boolean get() = this == RUNNING || this == STOPPED || this == TERMINATED
    val isOn: Boolean get() = this == RUNNING || this == PENDING
    val isTransitional: Boolean get() = this == PENDING || this == STOPPING || this == SHUTTING_DOWN
}
