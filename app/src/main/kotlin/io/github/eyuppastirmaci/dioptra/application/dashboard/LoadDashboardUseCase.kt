package io.github.eyuppastirmaci.dioptra.application.dashboard

import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisHealthClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisInfoClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisInfoParser
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisKeyspaceParser

class LoadDashboardUseCase(
    private val redisHealthClient: RedisHealthClient,
    private val redisInfoClient: RedisInfoClient,
    private val redisInfoParser: RedisInfoParser,
    private val redisKeyspaceParser: RedisKeyspaceParser,
) {

    /**
     * Loads the Redis dashboard data by checking Redis health and parsing server INFO output.
     */
    fun load(): RedisDashboardSnapshot {
        // Checks whether Redis is reachable before building the dashboard snapshot.
        val pingResponse = redisHealthClient.ping()

        // Fetches raw Redis server INFO output as the source data for the dashboard.
        val rawInfo = redisInfoClient.fetchServerInfo()

        // Converts raw Redis INFO output into a structured key-value representation.
        val info = redisInfoParser.parse(rawInfo)

        // Extracts keyspace metrics from the default Redis database.
        val keyspaceInfo = redisKeyspaceParser.parse(
            database = DEFAULT_DATABASE,
            rawValue = info.string(DEFAULT_DATABASE),
        )

        return RedisDashboardSnapshot(
            status = if (pingResponse == PONG_RESPONSE) "Connected" else "Unknown",
            redisVersion = info.string("redis_version") ?: "unknown",
            usedMemoryHuman = info.string("used_memory_human") ?: "unknown",
            connectedClients = info.int("connected_clients") ?: 0,
            totalCommandsProcessed = info.long("total_commands_processed") ?: 0L,
            keyspaceHits = info.long("keyspace_hits") ?: 0L,
            keyspaceMisses = info.long("keyspace_misses") ?: 0L,
            totalKeys = keyspaceInfo.keys,
        )
    }

    private companion object {
        const val PONG_RESPONSE = "PONG"
        const val DEFAULT_DATABASE = "db0"
    }
}