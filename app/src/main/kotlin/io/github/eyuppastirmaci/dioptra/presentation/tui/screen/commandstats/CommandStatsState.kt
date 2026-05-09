package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.commandstats

import io.github.eyuppastirmaci.dioptra.domain.commandstats.CommandStat

/**
 * Sort key for the commandstats list.
 *
 * The user can cycle between sort modes while on the screen:
 *   t = total usec (highest first)  — default, shows where most CPU time was spent
 *   c = calls count (highest first) — shows most-frequently-used commands
 *   a = average usec per call       — highlights commands with high per-call latency
 */
enum class CommandStatsSortMode(val label: String) {
    TOTAL_USEC("total µs"),
    CALLS("calls"),
    AVG_USEC("avg µs/call"),
}

sealed interface CommandStatsState {

    data object Loading : CommandStatsState

    data class Loaded(
        val stats: List<CommandStat>,
        val sortMode: CommandStatsSortMode = CommandStatsSortMode.TOTAL_USEC,
        val selectedIndex: Int = 0,
        val scrollOffset: Int = 0,
    ) : CommandStatsState {

        /** Returns stats re-sorted according to [sortMode]. */
        val sorted: List<CommandStat> get() = when (sortMode) {
            CommandStatsSortMode.TOTAL_USEC -> stats.sortedByDescending { it.totalUsec }
            CommandStatsSortMode.CALLS -> stats.sortedByDescending { it.calls }
            CommandStatsSortMode.AVG_USEC -> stats.sortedByDescending { it.usecPerCall }
        }
    }

    data class Error(val message: String) : CommandStatsState
}
