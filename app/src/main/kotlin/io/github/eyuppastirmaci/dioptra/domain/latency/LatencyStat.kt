package io.github.eyuppastirmaci.dioptra.domain.latency

/**
 * Per-command latency percentiles as reported by `INFO latencystats`.
 *
 * Available in Redis 7.0+ when latency tracking is enabled
 * (latency-tracking yes, which is the default since 7.0).
 *
 * All values are in microseconds.
 */
data class LatencyStat(
    val command: String,
    val p50Usec: Double,
    val p99Usec: Double,
    val p999Usec: Double,
)
