package io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage

class RedisMemoryUsageMapper {

    fun map(memoryUsageBytes: Long?): RedisKeyMemoryUsage {
        return if (memoryUsageBytes == null) {
            RedisKeyMemoryUsage.Unknown
        } else {
            RedisKeyMemoryUsage.Known(memoryUsageBytes)
        }
    }
}