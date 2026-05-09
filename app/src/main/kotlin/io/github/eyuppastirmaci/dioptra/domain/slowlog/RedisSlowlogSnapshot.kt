package io.github.eyuppastirmaci.dioptra.domain.slowlog

data class RedisSlowlogEntry(
    val id: Long,
    val timestampSeconds: Long,
    val durationMicroseconds: Long,
    val command: String,
    val arguments: List<String>,
    val clientAddress: String?,
    val clientName: String?,
)

data class RedisSlowlogSnapshot(
    val totalEntries: Long,
    val entries: List<RedisSlowlogEntry>,
)
