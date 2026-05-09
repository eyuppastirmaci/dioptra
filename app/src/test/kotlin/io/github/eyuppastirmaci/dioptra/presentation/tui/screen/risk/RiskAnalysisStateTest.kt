package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.risk

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyType
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskKeyFinding
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskLevel
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskPatternFinding
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskReason
import kotlin.test.Test
import kotlin.test.assertEquals

class RiskAnalysisStateTest {

    @Test
    fun `sorts visible keys by memory`() {
        val state = RiskAnalysisState.Loaded(
            snapshot = snapshot(
                topLargestKeys = listOf(
                    key("b", memory = 10),
                    key("a", memory = 20),
                )
            ),
            viewMode = RiskAnalysisViewMode.BIG_KEYS,
            sortMode = RiskAnalysisSortMode.MEMORY,
        )

        assertEquals(listOf("a", "b"), state.visibleKeys.map { it.name })
    }

    @Test
    fun `sorts visible patterns by risk`() {
        val state = RiskAnalysisState.Loaded(
            snapshot = snapshot(
                riskyPatterns = listOf(
                    pattern("watch", RiskLevel.WATCH),
                    pattern("critical", RiskLevel.CRITICAL),
                )
            ),
            viewMode = RiskAnalysisViewMode.PATTERNS,
            sortMode = RiskAnalysisSortMode.RISK,
        )

        assertEquals(listOf("critical", "watch"), state.visiblePatterns.map { it.patternName })
    }

    private fun snapshot(
        topLargestKeys: List<RiskKeyFinding> = emptyList(),
        riskyPatterns: List<RiskPatternFinding> = emptyList(),
    ): RiskAnalysisSnapshot {
        return RiskAnalysisSnapshot(
            analyzedKeyCount = 0,
            noTtlKeyCount = 0,
            bigKeyCount = 0,
            largeHashCount = 0,
            largeListCount = 0,
            largeSetCount = 0,
            largeZsetCount = 0,
            largeStreamCount = 0,
            topLargestKeys = topLargestKeys,
            topNoTtlKeys = emptyList(),
            riskyPatterns = riskyPatterns,
            warnings = emptyList(),
        )
    }

    private fun key(
        name: String,
        memory: Long,
    ): RiskKeyFinding {
        return RiskKeyFinding(
            name = name,
            type = RedisKeyType.STRING,
            ttl = RedisKeyTtlStatus.Expiring(60),
            memoryUsage = RedisKeyMemoryUsage.Known(memory),
            collectionSize = null,
            riskReasons = listOf(RiskReason.BIG_KEY),
        )
    }

    private fun pattern(
        name: String,
        level: RiskLevel,
    ): RiskPatternFinding {
        return RiskPatternFinding(
            patternName = name,
            keyCount = 1,
            noTtlKeyCount = 1,
            bigKeyCount = 0,
            largeCollectionKeyCount = 0,
            estimatedMemoryBytes = 1,
            riskLevel = level,
        )
    }
}
