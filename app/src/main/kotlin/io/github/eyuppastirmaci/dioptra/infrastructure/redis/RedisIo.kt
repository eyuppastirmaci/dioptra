package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> redisIo(block: () -> T): T {
    return withContext(Dispatchers.IO) {
        block()
    }
}
