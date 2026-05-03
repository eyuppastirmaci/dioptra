package io.github.eyuppastirmaci.dioptra.domain.dashboard

data class RedisDashboardSnapshot(
    val status: String,
    val activeConnectionName: String,
    val selectedDatabase: Int,
    val redisVersion: String,
    val uptime: String,
    val usedMemoryHuman: String,
    val memoryFragmentationHint: String,
    val memoryFragmentationHealthy: Boolean,
    val connectedClients: Int,
    val connectedClientsWarning: String,
    val connectedClientsHealthy: Boolean,
    val blockedClients: Int,
    val instantaneousOpsPerSecond: Int,
    val totalCommandsProcessed: Long,
    val keyspaceHits: Long,
    val keyspaceMisses: Long,
    val totalKeys: Long,
    val maxmemoryPolicy: String,
    val evictedKeys: Long,
)
