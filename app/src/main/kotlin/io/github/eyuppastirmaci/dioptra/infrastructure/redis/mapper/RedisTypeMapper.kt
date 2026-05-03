package io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyType

class RedisTypeMapper {

    fun map(rawType: String): RedisKeyType {
        return when (rawType.lowercase()) {
            "string" -> RedisKeyType.STRING
            "hash" -> RedisKeyType.HASH
            "list" -> RedisKeyType.LIST
            "set" -> RedisKeyType.SET
            "zset" -> RedisKeyType.ZSET
            "stream" -> RedisKeyType.STREAM
            "none" -> RedisKeyType.NONE
            else -> RedisKeyType.UNKNOWN
        }
    }
}