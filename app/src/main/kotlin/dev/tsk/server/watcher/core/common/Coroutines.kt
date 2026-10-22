package dev.tsk.server.watcher.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but rethrows [CancellationException] so structured concurrency keeps working.
 * A plain runCatching inside a suspend function turns cancellation into a Result.failure and the
 * coroutine carries on as if nothing happened.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
