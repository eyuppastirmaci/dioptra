package io.github.eyuppastirmaci.dioptra.application.dashboard

import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisHealthClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisInfoClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisInfoParser
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisKeyspaceParser
import java.util.Locale

class LoadDashboardUseCase(
    private val connectionConfig: RedisConnectionConfig,
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

        val selectedDatabaseKey = "db${connectionConfig.database}"

        // Extracts keyspace metrics from the selected Redis database.
        val keyspaceInfo = redisKeyspaceParser.parse(
            database = selectedDatabaseKey,
            rawValue = info.string(selectedDatabaseKey),
        )

        val rdbLastSaveTime = info.long("rdb_last_save_time") ?: 0L
        val rdbLastBgsaveStatus = info.string("rdb_last_bgsave_status") ?: "unknown"
        val aofEnabled = (info.int("aof_enabled") ?: 0) == 1
        val aofLastWriteStatus = info.string("aof_last_write_status") ?: "ok"
        val aofLastBgrewriteStatus = info.string("aof_last_bgrewrite_status") ?: "ok"

        val replicationRole = info.string("role") ?: "unknown"
        val connectedReplicas = info.int("connected_slaves") ?: 0
        val masterLinkStatus = info.string("master_link_status") ?: "n/a"
        val masterLastIoSecondsAgo = info.long("master_last_io_seconds_ago") ?: -1L

        return RedisDashboardSnapshot(
            status = if (pingResponse == PONG_RESPONSE) "Connected" else "Unknown",
            activeConnectionName = formatConnectionName(connectionConfig.name),
            selectedDatabase = connectionConfig.database,
            redisVersion = formatServerVersion(info.string("redis_version")),
            uptime = formatUptime(info.long("uptime_in_seconds")),
            usedMemoryHuman = info.string("used_memory_human") ?: "unknown",
            memoryFragmentationHint = formatMemoryFragmentationHint(info.double("mem_fragmentation_ratio")),
            memoryFragmentationHealthy = isMemoryFragmentationHealthy(info.double("mem_fragmentation_ratio")),
            connectedClients = info.int("connected_clients") ?: 0,
            connectedClientsWarning = formatConnectedClientsWarning(info.int("connected_clients") ?: 0),
            connectedClientsHealthy = isConnectedClientsHealthy(info.int("connected_clients") ?: 0),
            blockedClients = info.int("blocked_clients") ?: 0,
            instantaneousOpsPerSecond = info.int("instantaneous_ops_per_sec") ?: 0,
            totalCommandsProcessed = info.long("total_commands_processed") ?: 0L,
            keyspaceHits = info.long("keyspace_hits") ?: 0L,
            keyspaceMisses = info.long("keyspace_misses") ?: 0L,
            totalKeys = keyspaceInfo.keys,
            maxmemoryPolicy = formatMaxmemoryPolicy(info.string("maxmemory_policy")),
            evictedKeys = info.long("evicted_keys") ?: 0L,
            rdbStatus = rdbLastBgsaveStatus,
            rdbStatusHealthy = rdbLastBgsaveStatus == "ok",
            rdbLastSaveAge = formatSaveAge(rdbLastSaveTime),
            rdbChangesSinceLastSave = info.long("rdb_changes_since_last_save") ?: 0L,
            rdbBgsaveInProgress = (info.int("rdb_bgsave_in_progress") ?: 0) == 1,
            aofEnabled = aofEnabled,
            aofStatus = if (!aofEnabled) "disabled" else if (aofLastWriteStatus == "ok" && aofLastBgrewriteStatus == "ok") "ok" else "err",
            aofStatusHealthy = !aofEnabled || (aofLastWriteStatus == "ok" && aofLastBgrewriteStatus == "ok"),
            replicationRole = replicationRole,
            connectedReplicas = connectedReplicas,
            masterLinkStatus = masterLinkStatus,
            masterLinkHealthy = masterLinkStatus == "up",
            masterLastIoSecondsAgo = masterLastIoSecondsAgo,
        )
    }

    private fun formatConnectionName(name: String): String {
        return name.trim().takeIf { it.isNotEmpty() } ?: "Unknown"
    }

    private fun formatServerVersion(redisVersion: String?): String {
        val normalizedVersion = redisVersion
            ?.trim()
            ?.takeIf { version -> version.isNotEmpty() }

        return if (normalizedVersion == null) {
            "Unknown"
        } else {
            "Redis $normalizedVersion"
        }
    }

    private fun formatUptime(uptimeSeconds: Long?): String {
        val totalSeconds = uptimeSeconds ?: return "Unknown"
        val days = totalSeconds / SECONDS_PER_DAY
        val hours = (totalSeconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE

        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${totalSeconds}s"
        }
    }

    private fun formatMemoryFragmentationHint(fragmentationRatio: Double?): String {
        return when {
            fragmentationRatio == null -> "Unknown"
            fragmentationRatio >= HIGH_MEMORY_FRAGMENTATION_RATIO -> "High (${formatRatio(fragmentationRatio)})"
            fragmentationRatio < LOW_MEMORY_FRAGMENTATION_RATIO -> "Low (${formatRatio(fragmentationRatio)})"
            else -> "OK (${formatRatio(fragmentationRatio)})"
        }
    }

    private fun isMemoryFragmentationHealthy(fragmentationRatio: Double?): Boolean {
        return fragmentationRatio == null ||
                fragmentationRatio in LOW_MEMORY_FRAGMENTATION_RATIO..<HIGH_MEMORY_FRAGMENTATION_RATIO
    }

    private fun formatConnectedClientsWarning(connectedClients: Int): String {
        return when {
            connectedClients >= HIGH_CONNECTED_CLIENTS -> "High"
            connectedClients >= ELEVATED_CONNECTED_CLIENTS -> "Elevated"
            else -> "OK"
        }
    }

    private fun isConnectedClientsHealthy(connectedClients: Int): Boolean {
        return connectedClients < ELEVATED_CONNECTED_CLIENTS
    }

    private fun formatMaxmemoryPolicy(maxmemoryPolicy: String?): String {
        return maxmemoryPolicy
            ?.trim()
            ?.takeIf { policy -> policy.isNotEmpty() }
            ?: "Unknown"
    }

    private fun formatSaveAge(epochSeconds: Long): String {
        if (epochSeconds <= 0L) return "never"
        val nowSeconds = System.currentTimeMillis() / 1000L
        val ageSeconds = nowSeconds - epochSeconds
        return when {
            ageSeconds < 60L -> "${ageSeconds}s ago"
            ageSeconds < SECONDS_PER_HOUR -> "${ageSeconds / SECONDS_PER_MINUTE}m ago"
            ageSeconds < SECONDS_PER_DAY -> "${ageSeconds / SECONDS_PER_HOUR}h ago"
            else -> "${ageSeconds / SECONDS_PER_DAY}d ago"
        }
    }

    private fun formatRatio(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private companion object {
        const val PONG_RESPONSE = "PONG"
        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
        const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR
        const val ELEVATED_CONNECTED_CLIENTS = 100
        const val HIGH_CONNECTED_CLIENTS = 1_000
        const val LOW_MEMORY_FRAGMENTATION_RATIO = 0.90
        const val HIGH_MEMORY_FRAGMENTATION_RATIO = 1.50
    }
}
