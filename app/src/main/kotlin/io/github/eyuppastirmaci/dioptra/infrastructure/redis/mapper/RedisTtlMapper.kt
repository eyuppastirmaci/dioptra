package io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus

class RedisTtlMapper {

    fun map(ttlSeconds: Long): RedisKeyTtlStatus {
        return when {
            ttlSeconds == -2L -> RedisKeyTtlStatus.KeyDoesNotExist
            ttlSeconds == -1L -> RedisKeyTtlStatus.NoExpiration
            ttlSeconds >= 0L -> RedisKeyTtlStatus.Expiring(ttlSeconds)
            else -> RedisKeyTtlStatus.Unknown(ttlSeconds)
        }
    }
}