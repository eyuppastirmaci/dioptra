package io.github.eyuppastirmaci.dioptra.presentation.tui.format

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus

class RedisKeyTtlFormatter {

    fun format(ttl: RedisKeyTtlStatus): String {
        return when (ttl) {
            RedisKeyTtlStatus.KeyDoesNotExist -> "missing"
            RedisKeyTtlStatus.NoExpiration -> "! no ttl"
            is RedisKeyTtlStatus.Expiring -> "${ttl.seconds}s"
            is RedisKeyTtlStatus.Unknown -> "unknown(${ttl.rawValue})"
        }
    }
}
