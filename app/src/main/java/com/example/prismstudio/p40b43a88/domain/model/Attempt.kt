package com.example.prismstudio.p40b43a88.domain.model

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for work that may suspend: failures become a [Result], but coroutine cancellation
 * is rethrown so a cancelled caller stops instead of carrying on with a fake failure.
 */
inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
