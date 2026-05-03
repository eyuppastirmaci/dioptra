package io.github.eyuppastirmaci.dioptra.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

object DioptraCoroutineExceptionHandler {

    private val defaultLogger = LoggerFactory.getLogger("io.github.eyuppastirmaci.dioptra.coroutines")

    fun create(
        logger: Logger = defaultLogger,
        contextName: String,
        onError: ((Throwable) -> Unit)? = null,
    ): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _: CoroutineContext, throwable: Throwable ->
            if (throwable is CancellationException) {
                return@CoroutineExceptionHandler
            }

            logger.error("Unhandled coroutine failure in {}.", contextName, throwable)
            onError?.invoke(throwable)
        }
    }
}
