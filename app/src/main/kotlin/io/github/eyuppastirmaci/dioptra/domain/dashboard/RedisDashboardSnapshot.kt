package io.github.eyuppastirmaci.dioptra.domain.dashboard

data class RedisDashboardSnapshot(
    val status: String,
    val redisVersion: String,
    val usedMemoryHuman: String,
    val connectedClients: Int,
    val totalCommandsProcessed: Long,
    val keyspaceHits: Long,
    val keyspaceMisses: Long,
    val totalKeys: Long,
)