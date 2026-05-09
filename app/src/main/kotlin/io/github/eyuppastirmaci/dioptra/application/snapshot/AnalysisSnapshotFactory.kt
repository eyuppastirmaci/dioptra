package io.github.eyuppastirmaci.dioptra.application.snapshot

import io.github.eyuppastirmaci.dioptra.domain.commandstats.CommandStat
import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.domain.latency.LatencyStat
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceSummary
import io.github.eyuppastirmaci.dioptra.domain.report.MarkdownReportSnapshot
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskKeyFinding
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskPatternFinding
import io.github.eyuppastirmaci.dioptra.domain.slowlog.RedisSlowlogEntry
import io.github.eyuppastirmaci.dioptra.domain.suggestion.CommandSuggestion
import io.github.eyuppastirmaci.dioptra.domain.snapshot.AnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.snapshot.CommandStatSnapshot
import io.github.eyuppastirmaci.dioptra.domain.snapshot.CommandSuggestionSnapshot
import io.github.eyuppastirmaci.dioptra.domain.snapshot.DashboardSnapshot
import io.github.eyuppastirmaci.dioptra.domain.snapshot.LatencyStatSnapshot
import io.github.eyuppastirmaci.dioptra.domain.snapshot.NamespaceAnalysisSnapshotData
import io.github.eyuppastirmaci.dioptra.domain.snapshot.NamespaceSnapshot
import io.github.eyuppastirmaci.dioptra.domain.snapshot.RiskAnalysisSnapshotData
import io.github.eyuppastirmaci.dioptra.domain.snapshot.RiskKeySnapshot
import io.github.eyuppastirmaci.dioptra.domain.snapshot.RiskPatternSnapshot
import io.github.eyuppastirmaci.dioptra.domain.snapshot.RiskWarningSnapshot
import io.github.eyuppastirmaci.dioptra.domain.snapshot.SlowlogEntrySnapshot
import io.github.eyuppastirmaci.dioptra.domain.snapshot.SlowlogSnapshotData
import io.github.eyuppastirmaci.dioptra.domain.snapshot.UnexpectedNamespaceSnapshot

class AnalysisSnapshotFactory {

    fun create(source: MarkdownReportSnapshot): AnalysisSnapshot {
        return AnalysisSnapshot(
            schemaVersion = SCHEMA_VERSION,
            generatedAt = source.generatedAt.toString(),
            profileName = source.dashboard.activeConnectionName,
            selectedDatabase = source.dashboard.selectedDatabase,
            dashboard = source.dashboard.toSnapshot(),
            namespaceAnalysis = source.namespaceAnalysis.toSnapshot(),
            riskAnalysis = source.riskAnalysis.toSnapshot(),
            slowlog = SlowlogSnapshotData(
                totalEntries = source.slowlog.totalEntries,
                entries = source.slowlog.entries.map { it.toSnapshot() },
            ),
            commandStats = source.commandStats.map { it.toSnapshot() },
            latencyStats = source.latencyStats.map { it.toSnapshot() },
            commandSuggestions = source.commandSuggestions.map { it.toSnapshot() },
        )
    }

    private fun RedisDashboardSnapshot.toSnapshot(): DashboardSnapshot {
        return DashboardSnapshot(
            status = status,
            redisVersion = redisVersion,
            uptime = uptime,
            usedMemoryHuman = usedMemoryHuman,
            memoryFragmentationHint = memoryFragmentationHint,
            memoryFragmentationHealthy = memoryFragmentationHealthy,
            connectedClients = connectedClients,
            connectedClientsHealthy = connectedClientsHealthy,
            blockedClients = blockedClients,
            instantaneousOpsPerSecond = instantaneousOpsPerSecond,
            totalCommandsProcessed = totalCommandsProcessed,
            keyspaceHits = keyspaceHits,
            keyspaceMisses = keyspaceMisses,
            totalKeys = totalKeys,
            maxmemoryPolicy = maxmemoryPolicy,
            evictedKeys = evictedKeys,
            rdbStatus = rdbStatus,
            rdbStatusHealthy = rdbStatusHealthy,
            aofEnabled = aofEnabled,
            aofStatus = aofStatus,
            aofStatusHealthy = aofStatusHealthy,
            replicationRole = replicationRole,
            connectedReplicas = connectedReplicas,
            masterLinkStatus = masterLinkStatus,
            masterLinkHealthy = masterLinkHealthy,
        )
    }

    private fun NamespaceAnalysisSnapshot.toSnapshot(): NamespaceAnalysisSnapshotData {
        return NamespaceAnalysisSnapshotData(
            totalNamespaceCount = totalNamespaceCount,
            analyzedKeyCount = analyzedKeyCount,
            ignoredKeyCount = ignoredKeyCount,
            alertSuppressedKeyCount = alertSuppressedKeyCount,
            warnings = analysisWarnings,
            namespaces = summaries.map { it.toSnapshot() },
            topRiskyNamespaces = topRiskyNamespaces.map { it.identity.normalizedName },
            unexpectedNamespaces = unexpectedNamespaces.map {
                UnexpectedNamespaceSnapshot(
                    namespaceName = it.namespaceName,
                    reason = it.reason,
                )
            },
            anomalousNamespaces = anomalousNamespaces.map { it.identity.normalizedName },
        )
    }

    private fun NamespaceSummary.toSnapshot(): NamespaceSnapshot {
        return NamespaceSnapshot(
            name = identity.displayName,
            normalizedName = identity.normalizedName,
            keyCount = keyCount,
            averageTtlSeconds = averageTtlSeconds,
            noTtlKeyCount = noTtlKeyCount,
            estimatedMemoryBytes = when (val memory = estimatedMemoryUsage) {
                NamespaceMemoryUsage.Unknown -> null
                is NamespaceMemoryUsage.Estimated -> memory.totalBytes
            },
            ttlCoveragePercent = ttlCoverage.coveragePercent,
            memoryConcentrationPercent = memoryConcentrationPercent,
            healthScore = health.score,
            healthLevel = health.level.name,
            healthReason = health.primaryReason,
            riskReasons = riskReasons.map { it.name },
            namingAnomalies = namingAnomalies.map { "${it.keyName}: ${it.reason}" },
            unexpectedReason = unexpectedNamespaceSignal?.reason,
        )
    }

    private fun RiskAnalysisSnapshot.toSnapshot(): RiskAnalysisSnapshotData {
        return RiskAnalysisSnapshotData(
            analyzedKeyCount = analyzedKeyCount,
            noTtlKeyCount = noTtlKeyCount,
            bigKeyCount = bigKeyCount,
            largeCollectionKeyCount = largeCollectionKeyCount,
            topLargestKeys = topLargestKeys.map { it.toSnapshot() },
            topNoTtlKeys = topNoTtlKeys.map { it.toSnapshot() },
            riskyPatterns = riskyPatterns.map { it.toSnapshot() },
            warnings = warnings.map {
                RiskWarningSnapshot(
                    level = it.level.name,
                    message = it.message,
                )
            },
        )
    }

    private fun RiskKeyFinding.toSnapshot(): RiskKeySnapshot {
        val ttlStatus = ttl
        return RiskKeySnapshot(
            name = name,
            type = type.name,
            ttlStatus = ttlStatus::class.simpleName ?: "Unknown",
            ttlSeconds = (ttlStatus as? RedisKeyTtlStatus.Expiring)?.seconds,
            memoryBytes = when (val memory = memoryUsage) {
                RedisKeyMemoryUsage.Unknown -> null
                is RedisKeyMemoryUsage.Known -> memory.bytes
            },
            collectionSize = collectionSize,
            riskReasons = riskReasons.map { it.name },
        )
    }

    private fun RiskPatternFinding.toSnapshot(): RiskPatternSnapshot {
        return RiskPatternSnapshot(
            patternName = patternName,
            keyCount = keyCount,
            noTtlKeyCount = noTtlKeyCount,
            bigKeyCount = bigKeyCount,
            largeCollectionKeyCount = largeCollectionKeyCount,
            estimatedMemoryBytes = estimatedMemoryBytes,
            riskLevel = riskLevel.name,
        )
    }

    private fun RedisSlowlogEntry.toSnapshot(): SlowlogEntrySnapshot {
        return SlowlogEntrySnapshot(
            id = id,
            timestampSeconds = timestampSeconds,
            durationMicroseconds = durationMicroseconds,
            command = command,
            arguments = arguments,
            clientAddress = clientAddress,
            clientName = clientName,
        )
    }

    private fun CommandStat.toSnapshot(): CommandStatSnapshot {
        return CommandStatSnapshot(
            command = command,
            calls = calls,
            totalUsec = totalUsec,
            usecPerCall = usecPerCall,
            rejectedCalls = rejectedCalls,
            failedCalls = failedCalls,
        )
    }

    private fun LatencyStat.toSnapshot(): LatencyStatSnapshot {
        return LatencyStatSnapshot(
            command = command,
            p50Usec = p50Usec,
            p99Usec = p99Usec,
            p999Usec = p999Usec,
        )
    }

    private fun CommandSuggestion.toSnapshot(): CommandSuggestionSnapshot {
        return CommandSuggestionSnapshot(
            category = category.name,
            title = title,
            reason = reason,
            commands = commands,
        )
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}
