package io.github.eyuppastirmaci.dioptra.config

import kotlin.test.Test
import kotlin.test.assertEquals

class RiskAnalysisSettingsTest {

    @Test
    fun `uses balanced defaults`() {
        val settings = RiskAnalysisSettings()

        assertEquals(1_048_576L, settings.normalizedBigKeyThresholdBytes)
        assertEquals(10_000L, settings.normalizedLargeHashFieldThreshold)
        assertEquals(10_000L, settings.normalizedLargeListLengthThreshold)
        assertEquals(10_000L, settings.normalizedLargeSetMemberThreshold)
        assertEquals(10_000L, settings.normalizedLargeZsetMemberThreshold)
        assertEquals(10_000L, settings.normalizedLargeStreamEntryThreshold)
        assertEquals(20, settings.normalizedTopKeyCount)
        assertEquals(100L, settings.normalizedScanCount)
    }

    @Test
    fun `coerces invalid thresholds to safe minimums`() {
        val settings = RiskAnalysisSettings(
            bigKeyThresholdBytes = 0,
            largeHashFieldThreshold = -1,
            largeListLengthThreshold = 0,
            largeSetMemberThreshold = -5,
            largeZsetMemberThreshold = 0,
            largeStreamEntryThreshold = -10,
            topKeyCount = 0,
            scanCount = 0,
        )

        assertEquals(1L, settings.normalizedBigKeyThresholdBytes)
        assertEquals(1L, settings.normalizedLargeHashFieldThreshold)
        assertEquals(1L, settings.normalizedLargeListLengthThreshold)
        assertEquals(1L, settings.normalizedLargeSetMemberThreshold)
        assertEquals(1L, settings.normalizedLargeZsetMemberThreshold)
        assertEquals(1L, settings.normalizedLargeStreamEntryThreshold)
        assertEquals(1, settings.normalizedTopKeyCount)
        assertEquals(1L, settings.normalizedScanCount)
    }
}
