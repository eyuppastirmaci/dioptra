package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisSlowLogEntry
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisSlowLogParser
import io.lettuce.core.api.sync.RedisCommands

class RedisSlowlogClient(
    private val commands: RedisCommands<String, String>,
    private val parser: RedisSlowLogParser = RedisSlowLogParser(),
) {

    companion object {
        private const val DEFAULT_COUNT = 128
    }

    fun fetchEntries(count: Int = DEFAULT_COUNT): List<RedisSlowLogEntry> {
        @Suppress("UNCHECKED_CAST")
        val raw = commands.slowlogGet(count) as List<Any>
        return parser.parse(raw)
    }

    fun fetchLength(): Long {
        return commands.slowlogLen() ?: 0L
    }
}
