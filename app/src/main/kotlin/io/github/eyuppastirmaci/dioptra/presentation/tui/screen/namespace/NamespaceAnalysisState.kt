package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.namespace

import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceSummary

enum class NamespaceAnalysisSortMode(val label: String) {
    RISK("risk"),
    KEY_COUNT("keys"),
    MEMORY_CONCENTRATION("mem %"),
    TTL_COVERAGE("ttl %"),
    NO_TTL("no ttl"),
}

sealed interface NamespaceAnalysisState {

    data object Loading : NamespaceAnalysisState

    data class Loaded(
        val snapshot: NamespaceAnalysisSnapshot,
        val sortMode: NamespaceAnalysisSortMode = NamespaceAnalysisSortMode.RISK,
        val selectedIndex: Int = 0,
        val scrollOffset: Int = 0,
    ) : NamespaceAnalysisState {

        val sorted: List<NamespaceSummary>
            get() = when (sortMode) {
                NamespaceAnalysisSortMode.RISK -> snapshot.summaries.sortedWith(
                    compareBy<NamespaceSummary> { it.health.score }
                        .thenByDescending { it.memoryConcentrationPercent }
                        .thenByDescending { it.keyCount }
                )

                NamespaceAnalysisSortMode.KEY_COUNT -> snapshot.summaries.sortedByDescending { it.keyCount }
                NamespaceAnalysisSortMode.MEMORY_CONCENTRATION -> snapshot.summaries.sortedByDescending { it.memoryConcentrationPercent }
                NamespaceAnalysisSortMode.TTL_COVERAGE -> snapshot.summaries.sortedByDescending { it.ttlCoverage.coveragePercent }
                NamespaceAnalysisSortMode.NO_TTL -> snapshot.summaries.sortedByDescending { it.noTtlKeyCount }
            }

        val selectedSummary: NamespaceSummary?
            get() = sorted.getOrNull(selectedIndex)

        val totalEstimatedMemoryBytes: Long
            get() = snapshot.summaries.sumOf { summary ->
                when (val memoryUsage = summary.estimatedMemoryUsage) {
                    NamespaceMemoryUsage.Unknown -> 0L
                    is NamespaceMemoryUsage.Estimated -> memoryUsage.totalBytes
                }
            }
    }

    data class Error(val message: String) : NamespaceAnalysisState
}
