package io.github.eyuppastirmaci.dioptra.domain.report

import io.github.eyuppastirmaci.dioptra.domain.commandstats.CommandStat
import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.domain.latency.LatencyStat
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.slowlog.RedisSlowlogSnapshot
import io.github.eyuppastirmaci.dioptra.domain.suggestion.CommandSuggestion
import java.time.Instant

data class MarkdownReportSnapshot(
    val generatedAt: Instant,
    val dashboard: RedisDashboardSnapshot,
    val namespaceAnalysis: NamespaceAnalysisSnapshot,
    val riskAnalysis: RiskAnalysisSnapshot,
    val slowlog: RedisSlowlogSnapshot,
    val commandStats: List<CommandStat>,
    val latencyStats: List<LatencyStat>,
    val commandSuggestions: List<CommandSuggestion>,
)
