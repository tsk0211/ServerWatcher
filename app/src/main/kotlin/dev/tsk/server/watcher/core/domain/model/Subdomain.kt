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

/** Public hostname served by this subdomain, e.g. `api.example.com`. */
fun Subdomain.hostName(domain: Domain): String {
    val zone = domain.hostedZoneName?.ifBlank { null } ?: domain.name
    return if (isApex || prefix.isBlank()) zone else "$prefix.$zone"
}
