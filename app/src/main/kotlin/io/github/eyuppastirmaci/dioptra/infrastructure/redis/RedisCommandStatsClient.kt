package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.github.eyuppastirmaci.dioptra.domain.commandstats.CommandStat
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisCommandStatsParser
import io.lettuce.core.api.sync.RedisCommands

class RedisCommandStatsClient(
    private val commands: RedisCommands<String, String>,
    private val parser: RedisCommandStatsParser = RedisCommandStatsParser(),
) {

    fun fetchStats(): List<CommandStat> {
        val raw = commands.info("commandstats") ?: return emptyList()
        return parser.parse(raw)
    }
}
