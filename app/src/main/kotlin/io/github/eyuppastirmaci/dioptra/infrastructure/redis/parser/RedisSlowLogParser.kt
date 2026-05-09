package io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser

class RedisSlowLogParser {

    /**
     * Parses the raw List<Object> output from Lettuce's slowlogGet() into typed entries.
     *
     * Each Redis SLOWLOG entry is a nested list with the following structure:
     *   [0] Long  - entry id
     *   [1] Long  - Unix timestamp (seconds)
     *   [2] Long  - execution time in microseconds
     *   [3] List  - command and arguments
     *   [4] String? - client address (ip:port), Redis >= 4.0
     *   [5] String? - client name, Redis >= 4.0
     */
    fun parse(rawEntries: List<Any>): List<RedisSlowLogEntry> {
        return rawEntries.mapNotNull { raw -> parseEntry(raw) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEntry(raw: Any): RedisSlowLogEntry? {
        if (raw !is List<*>) return null
        val entry = raw as List<Any?>

        val id = entry.getOrNull(0) as? Long ?: return null
        val timestamp = entry.getOrNull(1) as? Long ?: return null
        val duration = entry.getOrNull(2) as? Long ?: return null

        val rawArgs = entry.getOrNull(3)
        val allArgs: List<String> = when (rawArgs) {
            is List<*> -> rawArgs.mapNotNull { arg -> decodeArg(arg) }
            else -> emptyList()
        }

        val command = allArgs.firstOrNull() ?: "UNKNOWN"
        val arguments = if (allArgs.size > 1) allArgs.drop(1) else emptyList()

        val clientAddress = decodeArg(entry.getOrNull(4))
        val clientName = decodeArg(entry.getOrNull(5))

        return RedisSlowLogEntry(
            id = id,
            timestampSeconds = timestamp,
            durationMicroseconds = duration,
            command = command,
            arguments = arguments,
            clientAddress = clientAddress,
            clientName = clientName,
        )
    }

    private fun decodeArg(raw: Any?): String? {
        return when (raw) {
            is String -> raw.ifEmpty { null }
            is ByteArray -> String(raw).ifEmpty { null }
            null -> null
            else -> raw.toString().ifEmpty { null }
        }
    }
}

data class RedisSlowLogEntry(
    val id: Long,
    val timestampSeconds: Long,
    val durationMicroseconds: Long,
    val command: String,
    val arguments: List<String>,
    val clientAddress: String?,
    val clientName: String?,
)