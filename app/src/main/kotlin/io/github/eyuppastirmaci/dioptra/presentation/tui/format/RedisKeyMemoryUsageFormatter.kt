package io.github.eyuppastirmaci.dioptra.presentation.tui.format

import io.github.eyuppastirmaci.dioptra.application.format.ByteSizeFormatter
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage

class RedisKeyMemoryUsageFormatter(
    private val byteSizeFormatter: ByteSizeFormatter,
    private val riskClassifier: RedisKeyRiskClassifier,
) {

    fun format(memoryUsage: RedisKeyMemoryUsage): String {
        return when (memoryUsage) {
            RedisKeyMemoryUsage.Unknown -> "unknown"
            is RedisKeyMemoryUsage.Known -> {
                val formattedBytes = byteSizeFormatter.format(memoryUsage.bytes)
                if (riskClassifier.isBigKey(memoryUsage)) {
                    "! $formattedBytes"
                } else {
                    formattedBytes
                }
            }
        }
    }
}
