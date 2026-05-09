package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.slowlog

import io.github.eyuppastirmaci.dioptra.domain.slowlog.RedisSlowlogSnapshot
import io.github.eyuppastirmaci.dioptra.domain.slowlog.SlowlogCommandGroup
import io.github.eyuppastirmaci.dioptra.domain.slowlog.SlowlogWarning

enum class SlowlogViewMode {
    LIST,
    GROUPED,
    WARNINGS,
}

sealed interface SlowlogState {

    data object Loading : SlowlogState

    data class Loaded(
        val snapshot: RedisSlowlogSnapshot,
        val groups: List<SlowlogCommandGroup>,
        val warnings: List<SlowlogWarning>,
        val viewMode: SlowlogViewMode = SlowlogViewMode.LIST,
        val selectedIndex: Int = 0,
        val scrollOffset: Int = 0,
    ) : SlowlogState

    data class Error(
        val message: String,
    ) : SlowlogState
}
