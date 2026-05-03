package io.github.eyuppastirmaci.dioptra.domain.key

data class RedisKeySummary(
    val name: String,
    val type: RedisKeyType,
    val ttl: RedisKeyTtlStatus,
    val memoryUsage: RedisKeyMemoryUsage,
)