package io.github.eyuppastirmaci.dioptra.application.snapshot

import io.github.eyuppastirmaci.dioptra.application.commandstats.LoadCommandStatsUseCase
import io.github.eyuppastirmaci.dioptra.application.latency.LoadLatencyStatsUseCase
import io.github.eyuppastirmaci.dioptra.application.namespace.LoadNamespaceAnalysisUseCase
import io.github.eyuppastirmaci.dioptra.application.risk.LoadRiskAnalysisUseCase
import io.github.eyuppastirmaci.dioptra.application.slowlog.LoadSlowlogUseCase
import io.github.eyuppastirmaci.dioptra.application.suggestion.CommandSuggestionEngine
import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.domain.report.MarkdownReportSnapshot
import io.github.eyuppastirmaci.dioptra.infrastructure.snapshot.AnalysisSnapshotFileWriter
import java.nio.file.Path
import java.time.Instant

data class SaveAnalysisSnapshotResult(
    val path: Path,
    val generatedAt: Instant,
    val schemaVersion: Int,
)

class SaveAnalysisSnapshotUseCase(
    private val loadNamespaceAnalysisUseCase: LoadNamespaceAnalysisUseCase,
    private val loadRiskAnalysisUseCase: LoadRiskAnalysisUseCase,
    private val loadSlowlogUseCase: LoadSlowlogUseCase,
    private val loadCommandStatsUseCase: LoadCommandStatsUseCase,
    private val loadLatencyStatsUseCase: LoadLatencyStatsUseCase,
    private val commandSuggestionEngine: CommandSuggestionEngine,
    private val snapshotFactory: AnalysisSnapshotFactory,
    private val writer: AnalysisSnapshotFileWriter,
) {

    suspend fun save(dashboard: RedisDashboardSnapshot): SaveAnalysisSnapshotResult {
        val generatedAt = Instant.now()
        val namespaceAnalysis = loadNamespaceAnalysisUseCase.load()
        val riskAnalysis = loadRiskAnalysisUseCase.load()
        val slowlog = loadSlowlogUseCase.load()
        val commandStats = loadCommandStatsUseCase.load()
        val latencyStats = loadLatencyStatsUseCase.load()
        val commandSuggestions = commandSuggestionEngine.generate(
            dashboard = dashboard,
            riskAnalysis = riskAnalysis,
        )

        val reportSnapshot = MarkdownReportSnapshot(
            generatedAt = generatedAt,
            dashboard = dashboard,
            namespaceAnalysis = namespaceAnalysis,
            riskAnalysis = riskAnalysis,
            slowlog = slowlog,
            commandStats = commandStats,
            latencyStats = latencyStats,
            commandSuggestions = commandSuggestions,
        )
        val snapshot = snapshotFactory.create(reportSnapshot)
        val path = writer.write(
            snapshot = snapshot,
            generatedAt = generatedAt,
            profileName = dashboard.activeConnectionName,
        )

        return SaveAnalysisSnapshotResult(
            path = path,
            generatedAt = generatedAt,
            schemaVersion = snapshot.schemaVersion,
        )
    }
}
