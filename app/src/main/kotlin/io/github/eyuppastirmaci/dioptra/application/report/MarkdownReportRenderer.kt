package io.github.eyuppastirmaci.dioptra.application.report

import io.github.eyuppastirmaci.dioptra.application.format.ByteSizeFormatter
import io.github.eyuppastirmaci.dioptra.domain.commandstats.CommandStat
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceSummary
import io.github.eyuppastirmaci.dioptra.domain.report.MarkdownReportSnapshot
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskKeyFinding
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskPatternFinding
import io.github.eyuppastirmaci.dioptra.domain.slowlog.RedisSlowlogEntry
import io.github.eyuppastirmaci.dioptra.domain.suggestion.CommandSuggestion
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MarkdownReportRenderer(
    private val byteSizeFormatter: ByteSizeFormatter,
) {

    fun render(snapshot: MarkdownReportSnapshot): String {
        return buildString {
            appendLine("# Dioptra Redis Report")
            appendLine()
            appendLine("- Generated at: ${TIMESTAMP_FORMATTER.format(snapshot.generatedAt)}")
            appendLine("- Profile: ${snapshot.dashboard.activeConnectionName}")
            appendLine("- Database: ${snapshot.dashboard.selectedDatabase}")
            appendLine()

            appendDashboard(snapshot)
            appendRiskAnalysis(snapshot.riskAnalysis)
            appendNamespaceAnalysis(snapshot.namespaceAnalysis)
            appendSlowlog(snapshot.slowlog.entries)
            appendCommandStats(snapshot.commandStats)
            appendLatencyStats(snapshot.latencyStats)
            appendSuggestions(snapshot.commandSuggestions)
        }
    }

    private fun StringBuilder.appendDashboard(snapshot: MarkdownReportSnapshot) {
        val dashboard = snapshot.dashboard
        appendLine("## Dashboard")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("| --- | --- |")
        appendMetric("Status", dashboard.status)
        appendMetric("Redis version", dashboard.redisVersion)
        appendMetric("Uptime", dashboard.uptime)
        appendMetric("Used memory", dashboard.usedMemoryHuman)
        appendMetric("Memory fragmentation", dashboard.memoryFragmentationHint)
        appendMetric("Connected clients", "${dashboard.connectedClients} ${dashboard.connectedClientsWarning}")
        appendMetric("Blocked clients", dashboard.blockedClients.toString())
        appendMetric("Ops/sec", dashboard.instantaneousOpsPerSecond.toString())
        appendMetric("Total commands processed", dashboard.totalCommandsProcessed.toString())
        appendMetric("Total keys", dashboard.totalKeys.toString())
        appendMetric("Keyspace hits", dashboard.keyspaceHits.toString())
        appendMetric("Keyspace misses", dashboard.keyspaceMisses.toString())
        appendMetric("Maxmemory policy", dashboard.maxmemoryPolicy)
        appendMetric("Evicted keys", dashboard.evictedKeys.toString())
        appendLine()

        appendLine("### Persistence")
        appendLine()
        appendLine("- RDB: ${dashboard.rdbStatus} (last save: ${dashboard.rdbLastSaveAge}, unsaved changes: ${dashboard.rdbChangesSinceLastSave})")
        appendLine("- AOF: ${dashboard.aofStatus}")
        appendLine()

        appendLine("### Replication")
        appendLine()
        appendLine("- Role: ${dashboard.replicationRole}")
        appendLine("- Connected replicas: ${dashboard.connectedReplicas}")
        appendLine("- Master link: ${dashboard.masterLinkStatus}")
        appendLine()
    }

    private fun StringBuilder.appendRiskAnalysis(risk: RiskAnalysisSnapshot) {
        appendLine("## Risk Analysis")
        appendLine()
        appendLine("- Analyzed keys: ${risk.analyzedKeyCount}")
        appendLine("- No-TTL keys: ${risk.noTtlKeyCount}")
        appendLine("- Big keys: ${risk.bigKeyCount}")
        appendLine("- Large collections: ${risk.largeCollectionKeyCount}")
        appendLine()

        appendWarnings(risk)
        appendKeyFindings("Top Largest Keys", risk.topLargestKeys)
        appendKeyFindings("Top No-TTL Keys", risk.topNoTtlKeys)
        appendRiskPatterns(risk.riskyPatterns)
    }

    private fun StringBuilder.appendWarnings(risk: RiskAnalysisSnapshot) {
        appendLine("### Policy Warnings")
        appendLine()
        if (risk.warnings.isEmpty()) {
            appendLine("No memory policy warnings.")
            appendLine()
            return
        }

        risk.warnings.forEach { warning ->
            appendLine("- **${warning.level.name.lowercase()}**: ${warning.message}")
        }
        appendLine()
    }

    private fun StringBuilder.appendKeyFindings(
        title: String,
        keys: List<RiskKeyFinding>,
    ) {
        appendLine("### $title")
        appendLine()
        if (keys.isEmpty()) {
            appendLine("No findings.")
            appendLine()
            return
        }

        appendLine("| Key | Type | TTL | Memory | Size | Reasons |")
        appendLine("| --- | --- | --- | --- | --- | --- |")
        keys.take(TABLE_LIMIT).forEach { key ->
            appendLine(
                "| ${escapeCell(key.name)} | ${key.type.name.lowercase()} | ${formatTtl(key.ttl)} | ${formatMemory(key.memoryUsage)} | ${key.collectionSize ?: "-"} | ${key.riskReasons.joinToString { it.name.lowercase() }} |"
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendRiskPatterns(patterns: List<RiskPatternFinding>) {
        appendLine("### Risky Patterns")
        appendLine()
        if (patterns.isEmpty()) {
            appendLine("No risky patterns.")
            appendLine()
            return
        }

        appendLine("| Pattern | Level | Keys | No TTL | Big | Large Collections | Estimated Memory |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        patterns.take(TABLE_LIMIT).forEach { pattern ->
            appendLine(
                "| ${escapeCell(pattern.patternName)} | ${pattern.riskLevel.name.lowercase()} | ${pattern.keyCount} | ${pattern.noTtlKeyCount} | ${pattern.bigKeyCount} | ${pattern.largeCollectionKeyCount} | ${byteSizeFormatter.format(pattern.estimatedMemoryBytes)} |"
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendNamespaceAnalysis(namespace: NamespaceAnalysisSnapshot) {
        appendLine("## Namespace Analysis")
        appendLine()
        appendLine("- Namespaces: ${namespace.totalNamespaceCount}")
        appendLine("- Analyzed keys: ${namespace.analyzedKeyCount}")
        appendLine("- Ignored keys: ${namespace.ignoredKeyCount}")
        appendLine("- Alert-suppressed keys: ${namespace.alertSuppressedKeyCount}")
        appendLine()

        if (namespace.analysisWarnings.isNotEmpty()) {
            appendLine("### Analysis Warnings")
            appendLine()
            namespace.analysisWarnings.forEach { appendLine("- $it") }
            appendLine()
        }

        appendNamespaceTable("Top Risky Namespaces", namespace.topRiskyNamespaces)
        appendNamespaceTable("Anomalous Namespaces", namespace.anomalousNamespaces)

        appendLine("### Unexpected Namespaces")
        appendLine()
        if (namespace.unexpectedNamespaces.isEmpty()) {
            appendLine("No unexpected namespaces.")
        } else {
            namespace.unexpectedNamespaces.take(TABLE_LIMIT).forEach { signal ->
                appendLine("- `${signal.namespaceName}`: ${signal.reason}")
            }
        }
        appendLine()
    }

    private fun StringBuilder.appendNamespaceTable(
        title: String,
        namespaces: List<NamespaceSummary>,
    ) {
        appendLine("### $title")
        appendLine()
        if (namespaces.isEmpty()) {
            appendLine("No findings.")
            appendLine()
            return
        }

        appendLine("| Namespace | Keys | Avg TTL | No TTL | Memory | Health | Reason |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        namespaces.take(TABLE_LIMIT).forEach { namespace ->
            appendLine(
                "| ${escapeCell(namespace.identity.displayName)} | ${namespace.keyCount} | ${namespace.averageTtlSeconds?.let(::formatDurationSeconds) ?: "n/a"} | ${namespace.noTtlKeyCount} | ${formatNamespaceMemory(namespace.estimatedMemoryUsage)} | ${namespace.health.level.name.lowercase()} (${namespace.health.score}) | ${escapeCell(namespace.health.primaryReason)} |"
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendSlowlog(entries: List<RedisSlowlogEntry>) {
        appendLine("## Slowlog")
        appendLine()
        if (entries.isEmpty()) {
            appendLine("No slowlog entries returned.")
            appendLine()
            return
        }

        appendLine("| Command | Duration | Client | Arguments |")
        appendLine("| --- | --- | --- | --- |")
        entries.take(10).forEach { entry ->
            val client = entry.clientName?.takeIf { it.isNotBlank() } ?: entry.clientAddress.orEmpty().ifBlank { "-" }
            appendLine(
                "| ${escapeCell(entry.command)} | ${formatMicroseconds(entry.durationMicroseconds)} | ${escapeCell(client)} | ${escapeCell(entry.arguments.joinToString(" ").take(80))} |"
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendCommandStats(stats: List<CommandStat>) {
        appendLine("## Command Stats")
        appendLine()
        if (stats.isEmpty()) {
            appendLine("No commandstats data returned.")
            appendLine()
            return
        }

        appendLine("| Command | Calls | Total Time | Avg | Rejected | Failed |")
        appendLine("| --- | --- | --- | --- | --- | --- |")
        stats.sortedByDescending { it.totalUsec }.take(TABLE_LIMIT).forEach { stat ->
            appendLine(
                "| ${escapeCell(stat.command)} | ${stat.calls} | ${formatMicroseconds(stat.totalUsec)} | ${formatMicroseconds(stat.usecPerCall)} | ${stat.rejectedCalls} | ${stat.failedCalls} |"
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendLatencyStats(stats: List<io.github.eyuppastirmaci.dioptra.domain.latency.LatencyStat>) {
        appendLine("## Latency Stats")
        appendLine()
        if (stats.isEmpty()) {
            appendLine("No latency stats returned.")
            appendLine()
            return
        }

        appendLine("| Command | p50 | p99 | p99.9 |")
        appendLine("| --- | --- | --- | --- |")
        stats.sortedByDescending { it.p99Usec }.take(TABLE_LIMIT).forEach { stat ->
            appendLine(
                "| ${escapeCell(stat.command)} | ${formatMicroseconds(stat.p50Usec)} | ${formatMicroseconds(stat.p99Usec)} | ${formatMicroseconds(stat.p999Usec)} |"
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendSuggestions(suggestions: List<CommandSuggestion>) {
        appendLine("## redis-cli Command Suggestions")
        appendLine()
        if (suggestions.isEmpty()) {
            appendLine("No suggestions generated.")
            appendLine()
            return
        }

        suggestions.take(TABLE_LIMIT).forEach { suggestion ->
            appendLine("### ${suggestion.title}")
            appendLine()
            appendLine("- Category: ${suggestion.category.label}")
            suggestion.reason?.let { appendLine("- Reason: $it") }
            appendLine()
            appendLine("```bash")
            suggestion.commands.forEach(::appendLine)
            appendLine("```")
            appendLine()
        }
    }

    private fun StringBuilder.appendMetric(label: String, value: String) {
        appendLine("| ${escapeCell(label)} | ${escapeCell(value)} |")
    }

    private fun formatMemory(memoryUsage: RedisKeyMemoryUsage): String {
        return when (memoryUsage) {
            RedisKeyMemoryUsage.Unknown -> "unknown"
            is RedisKeyMemoryUsage.Known -> byteSizeFormatter.format(memoryUsage.bytes)
        }
    }

    private fun formatNamespaceMemory(memoryUsage: NamespaceMemoryUsage): String {
        return when (memoryUsage) {
            NamespaceMemoryUsage.Unknown -> "unknown"
            is NamespaceMemoryUsage.Estimated -> byteSizeFormatter.format(memoryUsage.totalBytes)
        }
    }

    private fun formatTtl(ttl: RedisKeyTtlStatus): String {
        return when (ttl) {
            RedisKeyTtlStatus.KeyDoesNotExist -> "missing"
            RedisKeyTtlStatus.NoExpiration -> "none"
            is RedisKeyTtlStatus.Expiring -> formatDurationSeconds(ttl.seconds)
            is RedisKeyTtlStatus.Unknown -> "unknown"
        }
    }

    private fun formatDurationSeconds(seconds: Long): String {
        return when {
            seconds < 60L -> "${seconds}s"
            seconds < 3_600L -> "${seconds / 60}m"
            seconds < 86_400L -> "${seconds / 3_600}h"
            else -> "${seconds / 86_400}d"
        }
    }

    private fun formatMicroseconds(usec: Long): String = formatMicroseconds(usec.toDouble())

    private fun formatMicroseconds(usec: Double): String {
        return when {
            usec < 1_000.0 -> "${"%.0f".format(usec)}us"
            usec < 1_000_000.0 -> "${"%.1f".format(usec / 1_000.0)}ms"
            else -> "${"%.2f".format(usec / 1_000_000.0)}s"
        }
    }

    private fun escapeCell(value: String): String {
        return value
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("|", "\\|")
    }

    private companion object {
        const val TABLE_LIMIT = 10
        val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault())
    }
}
