package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.risk

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskKeyFinding
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskPatternFinding

enum class RiskAnalysisViewMode(val label: String) {
    OVERVIEW("overview"),
    BIG_KEYS("big keys"),
    NO_TTL("no ttl"),
    PATTERNS("patterns"),
}

enum class RiskAnalysisSortMode(val label: String) {
    RISK("risk"),
    MEMORY("memory"),
    COUNT("count"),
    NAME("name"),
}

sealed interface RiskAnalysisState {

    data object Loading : RiskAnalysisState

    data class Loaded(
        val snapshot: RiskAnalysisSnapshot,
        val viewMode: RiskAnalysisViewMode = RiskAnalysisViewMode.OVERVIEW,
        val sortMode: RiskAnalysisSortMode = RiskAnalysisSortMode.RISK,
        val selectedIndex: Int = 0,
        val scrollOffset: Int = 0,
    ) : RiskAnalysisState {

        val visibleKeys: List<RiskKeyFinding>
            get() {
                val keys = when (viewMode) {
                    RiskAnalysisViewMode.BIG_KEYS -> snapshot.topLargestKeys
                    RiskAnalysisViewMode.NO_TTL -> snapshot.topNoTtlKeys
                    else -> emptyList()
                }

                return when (sortMode) {
                    RiskAnalysisSortMode.RISK -> keys.sortedWith(
                        compareByDescending<RiskKeyFinding> { it.riskReasons.size }
                            .thenByDescending { it.memoryBytes ?: -1L }
                            .thenBy { it.name }
                    )

                    RiskAnalysisSortMode.MEMORY -> keys.sortedWith(
                        compareByDescending<RiskKeyFinding> { it.memoryBytes ?: -1L }
                            .thenBy { it.name }
                    )

                    RiskAnalysisSortMode.COUNT -> keys.sortedWith(
                        compareByDescending<RiskKeyFinding> { it.collectionSize ?: -1L }
                            .thenByDescending { it.memoryBytes ?: -1L }
                            .thenBy { it.name }
                    )

                    RiskAnalysisSortMode.NAME -> keys.sortedBy { it.name }
                }
            }

        val visiblePatterns: List<RiskPatternFinding>
            get() {
                if (viewMode != RiskAnalysisViewMode.PATTERNS) {
                    return emptyList()
                }

                return when (sortMode) {
                    RiskAnalysisSortMode.RISK -> snapshot.riskyPatterns.sortedWith(
                        compareByDescending<RiskPatternFinding> { it.riskLevel.ordinal }
                            .thenByDescending { it.estimatedMemoryBytes }
                            .thenBy { it.patternName }
                    )

                    RiskAnalysisSortMode.MEMORY -> snapshot.riskyPatterns.sortedByDescending { it.estimatedMemoryBytes }
                    RiskAnalysisSortMode.COUNT -> snapshot.riskyPatterns.sortedByDescending { it.noTtlKeyCount + it.bigKeyCount + it.largeCollectionKeyCount }
                    RiskAnalysisSortMode.NAME -> snapshot.riskyPatterns.sortedBy { it.patternName }
                }
            }

        val selectableCount: Int
            get() = when (viewMode) {
                RiskAnalysisViewMode.BIG_KEYS,
                RiskAnalysisViewMode.NO_TTL -> visibleKeys.size

                RiskAnalysisViewMode.PATTERNS -> visiblePatterns.size
                RiskAnalysisViewMode.OVERVIEW -> 0
            }

        val selectedKey: RiskKeyFinding?
            get() = visibleKeys.getOrNull(selectedIndex)

        val selectedPattern: RiskPatternFinding?
            get() = visiblePatterns.getOrNull(selectedIndex)

        private val RiskKeyFinding.memoryBytes: Long?
            get() = when (val usage = memoryUsage) {
                RedisKeyMemoryUsage.Unknown -> null
                is RedisKeyMemoryUsage.Known -> usage.bytes
            }
    }

    data class Error(val message: String) : RiskAnalysisState
}
