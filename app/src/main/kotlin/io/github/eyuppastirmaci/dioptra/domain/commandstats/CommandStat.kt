package io.github.eyuppastirmaci.dioptra.domain.commandstats

/**
 * Aggregated statistics for a single Redis command as reported by INFO commandstats.
 *
 * Redis reports:
 *   cmdstat_<cmd>:calls=N,usec=N,usec_per_call=N.NN,rejected_calls=N,failed_calls=N
 *
 * All duration values are in microseconds.
 */
data class CommandStat(
    val command: String,
    val calls: Long,
    val totalUsec: Long,
    val usecPerCall: Double,
    val rejectedCalls: Long,
    val failedCalls: Long,
)
