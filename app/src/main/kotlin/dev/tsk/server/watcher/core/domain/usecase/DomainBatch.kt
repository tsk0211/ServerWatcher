package dev.tsk.server.watcher.core.domain.usecase

import dev.tsk.server.watcher.core.domain.model.Instance
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal data class InstanceFailure(val instance: Instance, val error: Throwable)

/**
 * Runs [action] for every instance in parallel and collects the failures. One failing instance does
 * not stop the others, so a domain never ends up half-started just because the first call threw.
 */
internal suspend fun applyToAll(
    instances: List<Instance>,
    action: suspend (Instance) -> Result<Unit>,
): List<InstanceFailure> = coroutineScope {
    instances
        .map { instance -> async { instance to action(instance) } }
        .awaitAll()
        .mapNotNull { (instance, result) ->
            result.exceptionOrNull()?.let { InstanceFailure(instance, it) }
        }
}

internal fun partialFailure(verb: String, total: Int, failures: List<InstanceFailure>): Exception {
    val names = failures.joinToString { it.instance.label.ifBlank { it.instance.instanceId } }
    val reason = failures.first().error.message ?: failures.first().error::class.simpleName
    return IllegalStateException(
        "Could not $verb ${failures.size} of $total instance(s) ($names): $reason",
        failures.first().error,
    )
}
