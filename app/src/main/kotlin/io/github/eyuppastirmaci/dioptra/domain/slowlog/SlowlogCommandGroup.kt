package io.github.eyuppastirmaci.dioptra.domain.slowlog

import io.github.eyuppastirmaci.dioptra.presentation.tui.format.SlowlogRiskLevel

data class SlowlogCommandGroup(
    val command: String,
    val occurrences: Int,
    val totalDurationMicroseconds: Long,
    val minDurationMicroseconds: Long,
    val maxDurationMicroseconds: Long,
    val avgDurationMicroseconds: Long,
    val worstRiskLevel: SlowlogRiskLevel,
    val worstRiskReason: String,
)
