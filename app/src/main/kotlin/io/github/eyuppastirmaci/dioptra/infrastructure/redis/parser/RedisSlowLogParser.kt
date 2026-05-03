package io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser

class RedisSlowLogParser {

    /**
     * Reserved for converting Redis SLOWLOG entries into application-friendly models.
     */
    fun parse(): List<RedisSlowLogEntry> {
        return emptyList()
    }
}

data class RedisSlowLogEntry(
    val id: Long,
    val timestampSeconds: Long,
    val durationMicroseconds: Long,
    val command: String,
)