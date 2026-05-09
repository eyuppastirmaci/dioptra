package io.github.eyuppastirmaci.dioptra.presentation.tui.format

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage

class RedisKeyRiskClassifier(
    private val bigKeyThresholdBytes: Long = BIG_KEY_THRESHOLD_BYTES,
) {

    fun isBigKey(memoryUsage: RedisKeyMemoryUsage): Boolean {
        return memoryUsage is RedisKeyMemoryUsage.Known &&
            memoryUsage.bytes >= bigKeyThresholdBytes
    }

    private companion object {
        const val BIG_KEY_THRESHOLD_BYTES = 1024L * 1024L
    }
}
