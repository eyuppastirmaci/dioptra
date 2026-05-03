package io.github.eyuppastirmaci.dioptra.domain.key

sealed interface RedisKeyTtlStatus {

    data object KeyDoesNotExist : RedisKeyTtlStatus

    data object NoExpiration : RedisKeyTtlStatus

    data class Expiring(
        val seconds: Long,
    ) : RedisKeyTtlStatus

    data class Unknown(
        val rawValue: Long,
    ) : RedisKeyTtlStatus
}