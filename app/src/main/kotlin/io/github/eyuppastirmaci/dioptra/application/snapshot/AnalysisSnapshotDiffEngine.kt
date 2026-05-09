package io.github.eyuppastirmaci.dioptra.application.snapshot

import io.github.eyuppastirmaci.dioptra.application.format.ByteSizeFormatter
import io.github.eyuppastirmaci.dioptra.domain.snapshot.AnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.snapshot.NamespaceSnapshot
import io.github.eyuppastirmaci.dioptra.domain.snapshot.diff.AnalysisSnapshotDiff
import io.github.eyuppastirmaci.dioptra.domain.snapshot.diff.MetricDelta
import io.github.eyuppastirmaci.dioptra.domain.snapshot.diff.NamespaceDelta
import kotlin.math.abs

class AnalysisSnapshotDiffEngine(
    private val byteSizeFormatter: ByteSizeFormatter,
) {

    fun diff(
        baseline: AnalysisSnapshot,
        comparison: AnalysisSnapshot,
    ): AnalysisSnapshotDiff {
        val baselineNamespaces = baseline.namespaceAnalysis.namespaces.associateBy { it.normalizedName }
        val comparisonNamespaces = comparison.namespaceAnalysis.namespaces.associateBy { it.normalizedName }
        val baselineRiskKeys = baseline.riskAnalysis.riskKeyNames()
        val comparisonRiskKeys = comparison.riskAnalysis.riskKeyNames()
        val baselineWarnings = baseline.warningMessages()
        val comparisonWarnings = comparison.warningMessages()

        return AnalysisSnapshotDiff(
            baselineGeneratedAt = baseline.generatedAt,
            comparisonGeneratedAt = comparison.generatedAt,
            baselineProfileName = baseline.profileName,
            comparisonProfileName = comparison.profileName,
            metricDeltas = buildMetricDeltas(baseline, comparison),
            addedNamespaces = (comparisonNamespaces.keys - baselineNamespaces.keys).sorted(),
            removedNamespaces = (baselineNamespaces.keys - comparisonNamespaces.keys).sorted(),
            changedNamespaces = changedNamespaces(
                baselineNamespaces = baselineNamespaces,
                comparisonNamespaces = comparisonNamespaces,
            ),
            addedRiskKeys = (comparisonRiskKeys - baselineRiskKeys).sorted(),
            removedRiskKeys = (baselineRiskKeys - comparisonRiskKeys).sorted(),
            addedWarnings = (comparisonWarnings - baselineWarnings).sorted(),
            removedWarnings = (baselineWarnings - comparisonWarnings).sorted(),
        )
    }

    private fun buildMetricDeltas(
        baseline: AnalysisSnapshot,
        comparison: AnalysisSnapshot,
    ): List<MetricDelta> {
        return listOf(
            numericDelta("Total keys", baseline.dashboard.totalKeys, comparison.dashboard.totalKeys),
            numericDelta("Namespaces", baseline.namespaceAnalysis.totalNamespaceCount.toLong(), comparison.namespaceAnalysis.totalNamespaceCount.toLong()),
            numericDelta("Analyzed keys", baseline.namespaceAnalysis.analyzedKeyCount, comparison.namespaceAnalysis.analyzedKeyCount),
            numericDelta("No-TTL keys", baseline.riskAnalysis.noTtlKeyCount, comparison.riskAnalysis.noTtlKeyCount),
            numericDelta("Big keys", baseline.riskAnalysis.bigKeyCount, comparison.riskAnalysis.bigKeyCount),
            numericDelta("Large collections", baseline.riskAnalysis.largeCollectionKeyCount, comparison.riskAnalysis.largeCollectionKeyCount),
            numericDelta("Evicted keys", baseline.dashboard.evictedKeys, comparison.dashboard.evictedKeys),
            numericDelta("Blocked clients", baseline.dashboard.blockedClients.toLong(), comparison.dashboard.blockedClients.toLong()),
            numericDelta("Connected clients", baseline.dashboard.connectedClients.toLong(), comparison.dashboard.connectedClients.toLong()),
            numericDelta("Ops/sec", baseline.dashboard.instantaneousOpsPerSecond.toLong(), comparison.dashboard.instantaneousOpsPerSecond.toLong()),
            stringDelta("Maxmemory policy", baseline.dashboard.maxmemoryPolicy, comparison.dashboard.maxmemoryPolicy),
            stringDelta("Replication role", baseline.dashboard.replicationRole, comparison.dashboard.replicationRole),
            stringDelta("Master link", baseline.dashboard.masterLinkStatus, comparison.dashboard.masterLinkStatus),
        )
    }

    private fun changedNamespaces(
        baselineNamespaces: Map<String, NamespaceSnapshot>,
        comparisonNamespaces: Map<String, NamespaceSnapshot>,
    ): List<NamespaceDelta> {
        return baselineNamespaces.keys
            .intersect(comparisonNamespaces.keys)
            .mapNotNull { namespaceName ->
                val before = baselineNamespaces.getValue(namespaceName)
                val after = comparisonNamespaces.getValue(namespaceName)
                val delta = NamespaceDelta(
                    namespaceName = namespaceName,
                    keyCountDelta = after.keyCount - before.keyCount,
                    noTtlKeyCountDelta = after.noTtlKeyCount - before.noTtlKeyCount,
                    memoryBytesDelta = memoryDelta(before.estimatedMemoryBytes, after.estimatedMemoryBytes),
                    healthScoreDelta = after.healthScore - before.healthScore,
                )
                delta.takeIf { it.changed }
            }
            .sortedWith(
                compareByDescending<NamespaceDelta> { abs(it.keyCountDelta) }
                    .thenByDescending { abs(it.noTtlKeyCountDelta) }
                    .thenBy { it.namespaceName }
            )
            .take(NAMESPACE_DELTA_LIMIT)
    }

    private fun memoryDelta(
        before: Long?,
        after: Long?,
    ): Long? {
        if (before == null || after == null) {
            return null
        }
        return after - before
    }

    private fun numericDelta(
        label: String,
        before: Long,
        after: Long,
    ): MetricDelta {
        val delta = after - before
        return MetricDelta(
            label = label,
            before = before.toString(),
            after = after.toString(),
            delta = formatSigned(delta),
        )
    }

    private fun stringDelta(
        label: String,
        before: String,
        after: String,
    ): MetricDelta {
        return MetricDelta(
            label = label,
            before = before,
            after = after,
            delta = if (before == after) "same" else "changed",
        )
    }

    fun formatMemoryDelta(delta: Long?): String {
        return when {
            delta == null -> "unknown"
            delta == 0L -> "0 B"
            delta > 0L -> "+${byteSizeFormatter.format(delta)}"
            else -> "-${byteSizeFormatter.format(abs(delta))}"
        }
    }

    private fun formatSigned(value: Long): String {
        return when {
            value > 0L -> "+$value"
            else -> value.toString()
        }
    }

    private fun io.github.eyuppastirmaci.dioptra.domain.snapshot.RiskAnalysisSnapshotData.riskKeyNames(): Set<String> {
        return (topLargestKeys.map { it.name } + topNoTtlKeys.map { it.name }).toSet()
    }

    private fun AnalysisSnapshot.warningMessages(): Set<String> {
        return riskAnalysis.warnings.map { "${it.level}: ${it.message}" }.toSet() +
            namespaceAnalysis.warnings.map { "namespace: $it" }
    }

    private val NamespaceDelta.changed: Boolean
        get() = keyCountDelta != 0L ||
            noTtlKeyCountDelta != 0L ||
            memoryBytesDelta != 0L ||
            healthScoreDelta != 0

    private companion object {
        const val NAMESPACE_DELTA_LIMIT = 20
    }
}
