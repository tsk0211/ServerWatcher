package dev.tsk.server.watcher.core.domain.model

enum class HealthScheme(val label: String) {
    HTTP("http"),
    HTTPS("https");

    companion object {
        fun fromLabel(label: String?): HealthScheme = entries.firstOrNull { it.label == label } ?: HTTPS
    }
}

/** How a subdomain's health endpoint is reached. [port] null means the scheme's default. */
data class HealthCheckOptions(
    val scheme: HealthScheme = HealthScheme.HTTPS,
    val port: Int? = null,
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 10
    }
}

data class Subdomain(
    val id: String,
    val domainId: String,
    val prefix: String,
    val isApex: Boolean,
    val healthPath: String,
    val healthRangeStart: Int,
    val healthRangeEnd: Int,
    val healthIntervalSeconds: Int,
    val healthScheme: HealthScheme = HealthScheme.HTTPS,
    val healthPort: Int? = null,
    val healthTimeoutSeconds: Int = HealthCheckOptions.DEFAULT_TIMEOUT_SECONDS,
) {
    val healthOptions: HealthCheckOptions
        get() = HealthCheckOptions(healthScheme, healthPort, healthTimeoutSeconds)

    fun withHealthOptions(options: HealthCheckOptions) = copy(
        healthScheme = options.scheme,
        healthPort = options.port,
        healthTimeoutSeconds = options.timeoutSeconds,
    )
}

/** Public hostname served by this subdomain, e.g. `api.example.com`. */
fun Subdomain.hostName(domain: Domain): String {
    val zone = domain.hostedZoneName?.ifBlank { null } ?: domain.name
    return if (isApex || prefix.isBlank()) zone else "$prefix.$zone"
}

/** Origin the health check calls, e.g. `https://api.example.com:8443` (no path). */
fun Subdomain.healthBaseUrl(domain: Domain): String {
    val port = healthPort?.let { ":$it" }.orEmpty()
    return "${healthScheme.label}://${hostName(domain)}$port"
}
