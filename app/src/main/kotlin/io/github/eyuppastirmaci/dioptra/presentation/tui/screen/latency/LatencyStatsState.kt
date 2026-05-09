package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.latency

import io.github.eyuppastirmaci.dioptra.domain.latency.LatencyStat

/**
 * Sort key for the latency stats list.
 *
 * Default is P99 — the most meaningful signal for tail-latency issues.
 * The user can cycle between modes with 's'.
 */
enum class LatencyStatsSortMode(val label: String) {
    P99("p99"),
    P50("p50"),
    P999("p99.9"),
    COMMAND("command"),
}

sealed interface LatencyStatsState {

    data object Loading : LatencyStatsState

    data class Loaded(
        val stats: List<LatencyStat>,
        val sortMode: LatencyStatsSortMode = LatencyStatsSortMode.P99,
        val selectedIndex: Int = 0,
        val scrollOffset: Int = 0,
    ) : LatencyStatsState {

        val sorted: List<LatencyStat> get() = when (sortMode) {
            LatencyStatsSortMode.P99 -> stats.sortedByDescending { it.p99Usec }
            LatencyStatsSortMode.P50 -> stats.sortedByDescending { it.p50Usec }
            LatencyStatsSortMode.P999 -> stats.sortedByDescending { it.p999Usec }
            LatencyStatsSortMode.COMMAND -> stats.sortedBy { it.command }
        }
    }

    data class Error(val message: String) : LatencyStatsState
}
