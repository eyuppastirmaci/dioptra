package io.github.eyuppastirmaci.dioptra.domain.key

enum class RedisKeyType {
    STRING,
    HASH,
    LIST,
    SET,
    ZSET,
    STREAM,
    NONE,
    UNKNOWN,
}