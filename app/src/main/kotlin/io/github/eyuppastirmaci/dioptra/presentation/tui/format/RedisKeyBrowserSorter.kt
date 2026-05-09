package io.github.eyuppastirmaci.dioptra.presentation.tui.format

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeySummary
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus

class RedisKeyBrowserSorter {

    fun sort(
        keys: List<RedisKeySummary>,
        sortMode: RedisKeySortMode,
    ): List<RedisKeySummary> {
        return when (sortMode) {
            RedisKeySortMode.None -> keys
            RedisKeySortMode.Memory -> keys.sortedWith(
                compareByDescending<RedisKeySummary> { key -> memorySortValue(key.memoryUsage) }
                    .thenBy { key -> key.name }
            )
            RedisKeySortMode.Type -> keys.sortedWith(
                compareBy<RedisKeySummary> { key -> key.type.name }
                    .thenBy { key -> key.name }
            )
            RedisKeySortMode.Ttl -> keys.sortedWith(
                compareBy<RedisKeySummary> { key -> ttlSortValue(key.ttl) }
                    .thenBy { key -> key.name }
            )
        }
    }

    private fun memorySortValue(memoryUsage: RedisKeyMemoryUsage): Long {
        return when (memoryUsage) {
            RedisKeyMemoryUsage.Unknown -> -1L
            is RedisKeyMemoryUsage.Known -> memoryUsage.bytes
        }
    }

    private fun ttlSortValue(ttl: RedisKeyTtlStatus): Long {
        return when (ttl) {
            RedisKeyTtlStatus.NoExpiration -> Long.MAX_VALUE
            RedisKeyTtlStatus.KeyDoesNotExist -> Long.MAX_VALUE - 1
            is RedisKeyTtlStatus.Unknown -> Long.MAX_VALUE - 2
            is RedisKeyTtlStatus.Expiring -> ttl.seconds
        }
    }
}

enum class RedisKeySortMode(
    val label: String,
) {
    None("none"),
    Memory("memory desc"),
    Type("type asc"),
    Ttl("ttl asc"),
}
