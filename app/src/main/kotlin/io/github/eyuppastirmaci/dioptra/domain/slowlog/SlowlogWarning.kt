package io.github.eyuppastirmaci.dioptra.domain.slowlog

import io.github.eyuppastirmaci.dioptra.presentation.tui.format.SlowlogRiskLevel

data class SlowlogWarning(
    val severity: SlowlogRiskLevel,
    val command: String,
    val message: String,
)
