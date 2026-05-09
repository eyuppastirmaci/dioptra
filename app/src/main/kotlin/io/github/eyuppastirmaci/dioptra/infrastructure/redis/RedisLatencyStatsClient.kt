package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.github.eyuppastirmaci.dioptra.domain.latency.LatencyStat
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisLatencyStatsParser
import io.lettuce.core.api.sync.RedisCommands

class RedisLatencyStatsClient(
    private val commands: RedisCommands<String, String>,
    private val parser: RedisLatencyStatsParser = RedisLatencyStatsParser(),
) {

    fun fetchStats(): List<LatencyStat> {
        val raw = commands.info("latencystats") ?: return emptyList()
        return parser.parse(raw)
    }
}
