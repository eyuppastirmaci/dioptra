package io.github.eyuppastirmaci.dioptra.application.latency

import io.github.eyuppastirmaci.dioptra.domain.latency.LatencyStat
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisLatencyStatsClient

class LoadLatencyStatsUseCase(
    private val redisLatencyStatsClient: RedisLatencyStatsClient,
) {

    fun load(): List<LatencyStat> {
        return redisLatencyStatsClient.fetchStats()
    }
}
