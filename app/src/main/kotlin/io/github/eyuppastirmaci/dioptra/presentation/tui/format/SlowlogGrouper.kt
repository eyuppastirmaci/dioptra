package io.github.eyuppastirmaci.dioptra.presentation.tui.format

import io.github.eyuppastirmaci.dioptra.domain.slowlog.RedisSlowlogEntry
import io.github.eyuppastirmaci.dioptra.domain.slowlog.SlowlogCommandGroup

/**
 * Groups slowlog entries by command name, aggregating timing stats and determining
 * the worst risk level across all occurrences of each command.
 *
 * Result is sorted by total cumulative duration descending so the most expensive
 * commands appear first — regardless of how any single call performed.
 */
class SlowlogGrouper(
    private val riskClassifier: SlowlogRiskClassifier,
) {

    fun group(entries: List<RedisSlowlogEntry>): List<SlowlogCommandGroup> {
        if (entries.isEmpty()) return emptyList()

        return entries
            .groupBy { it.command.uppercase() }
            .map { (command, groupEntries) -> buildGroup(command, groupEntries) }
            .sortedByDescending { it.totalDurationMicroseconds }
    }

    private fun buildGroup(
        command: String,
        entries: List<RedisSlowlogEntry>,
    ): SlowlogCommandGroup {
        val durations = entries.map { it.durationMicroseconds }
        val totalDuration = durations.sum()

        val assessments = entries.map { riskClassifier.classify(it) }
        val worstAssessment = assessments.maxByOrNull { it.level.ordinal }
            ?: assessments.first()

        return SlowlogCommandGroup(
            command = command,
            occurrences = entries.size,
            totalDurationMicroseconds = totalDuration,
            minDurationMicroseconds = durations.min(),
            maxDurationMicroseconds = durations.max(),
            avgDurationMicroseconds = totalDuration / entries.size,
            worstRiskLevel = worstAssessment.level,
            worstRiskReason = worstAssessment.reason,
        )
    }
}
