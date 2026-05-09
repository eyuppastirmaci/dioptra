package io.github.eyuppastirmaci.dioptra.domain.snapshot.diff

data class AnalysisSnapshotDiff(
    val baselineGeneratedAt: String,
    val comparisonGeneratedAt: String,
    val baselineProfileName: String,
    val comparisonProfileName: String,
    val metricDeltas: List<MetricDelta>,
    val addedNamespaces: List<String>,
    val removedNamespaces: List<String>,
    val changedNamespaces: List<NamespaceDelta>,
    val addedRiskKeys: List<String>,
    val removedRiskKeys: List<String>,
    val addedWarnings: List<String>,
    val removedWarnings: List<String>,
) {
    val hasChanges: Boolean
        get() = metricDeltas.any { it.changed } ||
            addedNamespaces.isNotEmpty() ||
            removedNamespaces.isNotEmpty() ||
            changedNamespaces.isNotEmpty() ||
            addedRiskKeys.isNotEmpty() ||
            removedRiskKeys.isNotEmpty() ||
            addedWarnings.isNotEmpty() ||
            removedWarnings.isNotEmpty()
}

data class MetricDelta(
    val label: String,
    val before: String,
    val after: String,
    val delta: String,
) {
    val changed: Boolean get() = before != after
}

data class NamespaceDelta(
    val namespaceName: String,
    val keyCountDelta: Long,
    val noTtlKeyCountDelta: Long,
    val memoryBytesDelta: Long?,
    val healthScoreDelta: Int,
)
