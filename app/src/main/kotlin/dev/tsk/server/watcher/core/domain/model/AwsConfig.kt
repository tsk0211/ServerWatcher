package dev.tsk.server.watcher.core.domain.model

data class AwsConfig(
    val userPoolId: String,
    val clientId: String,
    val identityPoolId: String,
    val region: String,
)
