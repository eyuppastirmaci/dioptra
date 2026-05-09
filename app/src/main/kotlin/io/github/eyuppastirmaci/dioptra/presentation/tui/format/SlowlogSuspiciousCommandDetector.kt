package io.github.eyuppastirmaci.dioptra.presentation.tui.format

import io.github.eyuppastirmaci.dioptra.domain.slowlog.SlowlogCommandGroup
import io.github.eyuppastirmaci.dioptra.domain.slowlog.SlowlogWarning

/**
 * Produces actionable warning messages from grouped slowlog data.
 *
 * Each rule targets a specific suspicious pattern and yields a concrete warning
 * with an explanation. Rules are ordered by severity: CRITICAL first, then HIGH.
 * The detector intentionally avoids duplicating the classifier's logic — it operates
 * on already-grouped data and focuses on patterns that benefit from occurrence counts
 * (e.g., "KEYS was called 5 times") rather than per-entry risk scoring.
 */
class SlowlogSuspiciousCommandDetector {

    fun detect(groups: List<SlowlogCommandGroup>): List<SlowlogWarning> {
        return groups
            .flatMap { group -> rulesFor(group) }
            .sortedWith(compareByDescending<SlowlogWarning> { it.severity.ordinal }.thenBy { it.command })
    }

    private fun rulesFor(group: SlowlogCommandGroup): List<SlowlogWarning> {
        val warnings = mutableListOf<SlowlogWarning>()
        val cmd = group.command.uppercase()
        val n = group.occurrences

        when (cmd) {
            "KEYS" -> warnings += SlowlogWarning(
                severity = SlowlogRiskLevel.CRITICAL,
                command = cmd,
                message = "KEYS appeared $n time(s). It performs a full keyspace scan and " +
                    "blocks the server for its entire duration. Replace with SCAN.",
            )
            "FLUSHALL" -> warnings += SlowlogWarning(
                severity = SlowlogRiskLevel.CRITICAL,
                command = cmd,
                message = "FLUSHALL was executed $n time(s). This deletes every key across " +
                    "ALL databases. Verify this was intentional.",
            )
            "FLUSHDB" -> warnings += SlowlogWarning(
                severity = SlowlogRiskLevel.CRITICAL,
                command = cmd,
                message = "FLUSHDB was executed $n time(s). All keys in the selected " +
                    "database were deleted. Verify this was intentional.",
            )
            "DEBUG" -> warnings += SlowlogWarning(
                severity = SlowlogRiskLevel.CRITICAL,
                command = cmd,
                message = "DEBUG command appeared $n time(s). DEBUG SLEEP or DEBUG RELOAD " +
                    "can stall the server. Remove all DEBUG usage from production code.",
            )
            "EVAL", "EVALSHA" -> {
                warnings += SlowlogWarning(
                    severity = SlowlogRiskLevel.CRITICAL,
                    command = cmd,
                    message = "$cmd ran $n time(s) with avg ${formatDuration(group.avgDurationMicroseconds)}. " +
                        "Lua scripts block Redis for their full duration. " +
                        "Ensure scripts are short and avoid any blocking calls inside them.",
                )
                if (group.avgDurationMicroseconds >= 100_000L) {
                    warnings += SlowlogWarning(
                        severity = SlowlogRiskLevel.CRITICAL,
                        command = cmd,
                        message = "$cmd avg latency is ${formatDuration(group.avgDurationMicroseconds)} — " +
                            "above 100ms. Long-running Lua scripts block all other clients.",
                    )
                }
            }
            "WAIT" -> warnings += SlowlogWarning(
                severity = SlowlogRiskLevel.CRITICAL,
                command = cmd,
                message = "WAIT appeared $n time(s). It blocks the client until replicas " +
                    "acknowledge writes. Avoid using WAIT in hot paths.",
            )
            "HGETALL" -> warnings += SlowlogWarning(
                severity = SlowlogRiskLevel.HIGH,
                command = cmd,
                message = "HGETALL ran $n time(s). It fetches every field in a hash. " +
                    "Use HGET or HMGET for specific fields, or HSCAN for large hashes.",
            )
            "SMEMBERS" -> warnings += SlowlogWarning(
                severity = SlowlogRiskLevel.HIGH,
                command = cmd,
                message = "SMEMBERS ran $n time(s). It returns the entire set. " +
                    "Use SSCAN to iterate over large sets incrementally.",
            )
            "SORT" -> warnings += SlowlogWarning(
                severity = SlowlogRiskLevel.HIGH,
                command = cmd,
                message = "SORT ran $n time(s) with max latency ${formatDuration(group.maxDurationMicroseconds)}. " +
                    "SORT without an index is O(N log N). Add a LIMIT clause or pre-sort data.",
            )
            "SUNION", "SINTER", "SDIFF" -> warnings += SlowlogWarning(
                severity = SlowlogRiskLevel.HIGH,
                command = cmd,
                message = "$cmd ran $n time(s). It operates on entire sets. " +
                    "For large sets use the *STORE variant and cache the result.",
            )
            "ZUNIONSTORE", "ZINTERSTORE", "ZDIFFSTORE" -> warnings += SlowlogWarning(
                severity = SlowlogRiskLevel.HIGH,
                command = cmd,
                message = "$cmd ran $n time(s). Aggregating sorted sets is O(N log N). " +
                    "Cache the result and refresh periodically if the input sets are stable.",
            )
        }

        // Cross-cutting rules that apply regardless of command name
        if (n >= REPEATED_THRESHOLD_HIGH && group.worstRiskLevel.ordinal >= SlowlogRiskLevel.HIGH.ordinal) {
            warnings += SlowlogWarning(
                severity = SlowlogRiskLevel.HIGH,
                command = cmd,
                message = "$cmd appeared $n times in the slowlog window. " +
                    "High-risk commands repeating at this frequency indicate a systemic issue.",
            )
        }

        if (group.maxDurationMicroseconds >= DURATION_EXTREME_US) {
            warnings += SlowlogWarning(
                severity = SlowlogRiskLevel.CRITICAL,
                command = cmd,
                message = "A single $cmd call took ${formatDuration(group.maxDurationMicroseconds)}. " +
                    "Calls exceeding 1 second block the entire Redis server.",
            )
        }

        return warnings
    }

    private fun formatDuration(microseconds: Long): String {
        return when {
            microseconds < 1_000L -> "${microseconds}µs"
            microseconds < 1_000_000L -> "${"%.1f".format(microseconds / 1_000.0)}ms"
            else -> "${"%.2f".format(microseconds / 1_000_000.0)}s"
        }
    }

    private companion object {
        const val REPEATED_THRESHOLD_HIGH = 5
        const val DURATION_EXTREME_US = 1_000_000L  // 1 second
    }
}
