package io.github.eyuppastirmaci.dioptra.presentation.tui.format

import io.github.eyuppastirmaci.dioptra.domain.slowlog.RedisSlowlogEntry

enum class SlowlogRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

data class SlowlogRiskAssessment(
    val level: SlowlogRiskLevel,
    val reason: String,
)

/**
 * Classifies each slowlog entry with a risk level based on command type and execution duration.
 *
 * Two axes are evaluated independently and the higher result wins:
 *   1. Command risk  — what the command is and what arguments it was called with
 *   2. Duration risk — how long the command actually took
 */
class SlowlogRiskClassifier {

    fun classify(entry: RedisSlowlogEntry): SlowlogRiskAssessment {
        val durationAssessment = classifyByDuration(entry.durationMicroseconds)
        val commandAssessment = classifyByCommand(entry.command.uppercase(), entry.arguments)
        return if (commandAssessment.level.ordinal >= durationAssessment.level.ordinal) {
            commandAssessment
        } else {
            durationAssessment
        }
    }

    // ─── Duration axis ────────────────────────────────────────────────────────

    private fun classifyByDuration(microseconds: Long): SlowlogRiskAssessment {
        return when {
            microseconds >= DURATION_CRITICAL_US -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.CRITICAL,
                reason = "Execution exceeded 1 second (${formatDuration(microseconds)})",
            )
            microseconds >= DURATION_HIGH_US -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.HIGH,
                reason = "Execution exceeded 100ms (${formatDuration(microseconds)})",
            )
            microseconds >= DURATION_MEDIUM_US -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.MEDIUM,
                reason = "Execution exceeded 10ms (${formatDuration(microseconds)})",
            )
            else -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.LOW,
                reason = "Normal execution time (${formatDuration(microseconds)})",
            )
        }
    }

    // ─── Command axis ─────────────────────────────────────────────────────────

    private fun classifyByCommand(command: String, args: List<String>): SlowlogRiskAssessment {
        return when (command) {
            // ── CRITICAL: full-scan or destructive ───────────────────────────
            "KEYS" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.CRITICAL,
                reason = "KEYS causes a full keyspace scan and blocks the server",
            )
            "FLUSHALL" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.CRITICAL,
                reason = "FLUSHALL deletes all keys across all databases",
            )
            "FLUSHDB" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.CRITICAL,
                reason = "FLUSHDB deletes all keys in the current database",
            )
            "DEBUG" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.CRITICAL,
                reason = "DEBUG command should not be used in production",
            )
            "EVAL", "EVALSHA" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.CRITICAL,
                reason = "Lua scripts block the server for their entire duration",
            )
            "WAIT" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.CRITICAL,
                reason = "WAIT blocks until replicas acknowledge — use only deliberately",
            )

            // ── HIGH: O(N) over full collection ──────────────────────────────
            "HGETALL" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.HIGH,
                reason = "HGETALL retrieves all fields of a hash — O(N) on hash size",
            )
            "HKEYS", "HVALS" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.HIGH,
                reason = "$command retrieves all hash keys/values — O(N) on hash size",
            )
            "SMEMBERS" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.HIGH,
                reason = "SMEMBERS retrieves all members of a set — O(N) on set size",
            )
            "SUNION", "SDIFF", "SINTER" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.HIGH,
                reason = "$command operates over entire sets — O(N) across combined size",
            )
            "SUNIONSTORE", "SDIFFSTORE", "SINTERSTORE" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.HIGH,
                reason = "$command operates over entire sets and writes result — O(N)",
            )
            "SORT" -> classifySortCommand(args)
            "LRANGE" -> classifyLrangeCommand(args)
            "ZRANGE", "ZREVRANGE", "ZRANGEBYSCORE", "ZREVRANGEBYSCORE",
            "ZRANGEBYLEX", "ZREVRANGEBYLEX" -> classifyZrangeCommand(command, args)
            "ZUNIONSTORE", "ZINTERSTORE", "ZDIFFSTORE" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.HIGH,
                reason = "$command aggregates sorted sets — O(N log N) on total size",
            )

            // ── MEDIUM: batched or potentially large ─────────────────────────
            "MGET", "MSET", "MSETNX" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.MEDIUM,
                reason = "$command with ${args.size} keys — consider pipelining instead",
            )
            "DEL", "UNLINK" -> classifyMultiKeyDelete(command, args)
            "OBJECT" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.MEDIUM,
                reason = "OBJECT introspection can be expensive on large values",
            )
            "MEMORY" -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.MEDIUM,
                reason = "MEMORY USAGE walks the full object graph",
            )

            // ── LOW: everything else ──────────────────────────────────────────
            else -> SlowlogRiskAssessment(
                level = SlowlogRiskLevel.LOW,
                reason = "No known command-level risk for $command",
            )
        }
    }

    private fun classifySortCommand(args: List<String>): SlowlogRiskAssessment {
        val hasLimit = args.any { it.equals("LIMIT", ignoreCase = true) }
        return if (hasLimit) {
            SlowlogRiskAssessment(
                level = SlowlogRiskLevel.MEDIUM,
                reason = "SORT with LIMIT — verify sort key index exists",
            )
        } else {
            SlowlogRiskAssessment(
                level = SlowlogRiskLevel.HIGH,
                reason = "SORT without LIMIT sorts the entire collection — O(N log N)",
            )
        }
    }

    private fun classifyLrangeCommand(args: List<String>): SlowlogRiskAssessment {
        val start = args.getOrNull(0)?.toLongOrNull()
        val stop = args.getOrNull(1)?.toLongOrNull()
        val isUnbounded = start == 0L && stop == -1L
        return if (isUnbounded) {
            SlowlogRiskAssessment(
                level = SlowlogRiskLevel.HIGH,
                reason = "LRANGE 0 -1 retrieves the entire list — O(N)",
            )
        } else {
            SlowlogRiskAssessment(
                level = SlowlogRiskLevel.LOW,
                reason = "LRANGE with bounded range",
            )
        }
    }

    private fun classifyZrangeCommand(command: String, args: List<String>): SlowlogRiskAssessment {
        val hasLimit = args.any { it.equals("LIMIT", ignoreCase = true) }
        return if (!hasLimit) {
            SlowlogRiskAssessment(
                level = SlowlogRiskLevel.MEDIUM,
                reason = "$command without LIMIT may return large result sets",
            )
        } else {
            SlowlogRiskAssessment(
                level = SlowlogRiskLevel.LOW,
                reason = "$command with LIMIT",
            )
        }
    }

    private fun classifyMultiKeyDelete(command: String, args: List<String>): SlowlogRiskAssessment {
        return if (args.size > MULTI_KEY_THRESHOLD) {
            SlowlogRiskAssessment(
                level = SlowlogRiskLevel.MEDIUM,
                reason = "$command on ${args.size} keys at once — consider batching",
            )
        } else {
            SlowlogRiskAssessment(
                level = SlowlogRiskLevel.LOW,
                reason = "No known command-level risk for $command",
            )
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun formatDuration(microseconds: Long): String {
        return when {
            microseconds < 1_000L -> "${microseconds}µs"
            microseconds < 1_000_000L -> "${"%.1f".format(microseconds / 1_000.0)}ms"
            else -> "${"%.2f".format(microseconds / 1_000_000.0)}s"
        }
    }

    private companion object {
        const val DURATION_CRITICAL_US = 1_000_000L   // 1 second
        const val DURATION_HIGH_US = 100_000L          // 100 ms
        const val DURATION_MEDIUM_US = 10_000L         // 10 ms
        const val MULTI_KEY_THRESHOLD = 50
    }
}
