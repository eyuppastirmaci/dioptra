package io.github.eyuppastirmaci.dioptra.domain.key

sealed interface RedisKeyMemoryUsage {

    data object Unknown : RedisKeyMemoryUsage

    data class Known(
        val bytes: Long,
    ) : RedisKeyMemoryUsage
}