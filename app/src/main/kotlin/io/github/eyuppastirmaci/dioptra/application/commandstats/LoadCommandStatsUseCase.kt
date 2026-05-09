package io.github.eyuppastirmaci.dioptra.application.commandstats

import io.github.eyuppastirmaci.dioptra.domain.commandstats.CommandStat
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisCommandStatsClient

class LoadCommandStatsUseCase(
    private val redisCommandStatsClient: RedisCommandStatsClient,
) {

    fun load(): List<CommandStat> {
        return redisCommandStatsClient.fetchStats()
    }
}
