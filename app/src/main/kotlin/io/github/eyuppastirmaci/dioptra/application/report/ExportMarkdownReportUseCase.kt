package io.github.eyuppastirmaci.dioptra.application.report

import io.github.eyuppastirmaci.dioptra.application.commandstats.LoadCommandStatsUseCase
import io.github.eyuppastirmaci.dioptra.application.latency.LoadLatencyStatsUseCase
import io.github.eyuppastirmaci.dioptra.application.namespace.LoadNamespaceAnalysisUseCase
import io.github.eyuppastirmaci.dioptra.application.risk.LoadRiskAnalysisUseCase
import io.github.eyuppastirmaci.dioptra.application.slowlog.LoadSlowlogUseCase
import io.github.eyuppastirmaci.dioptra.application.suggestion.CommandSuggestionEngine
import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.domain.report.MarkdownReportSnapshot
import io.github.eyuppastirmaci.dioptra.infrastructure.report.MarkdownReportFileWriter
import java.nio.file.Path
import java.time.Instant

data class ExportMarkdownReportResult(
    val path: Path,
    val generatedAt: Instant,
)

class ExportMarkdownReportUseCase(
    private val loadNamespaceAnalysisUseCase: LoadNamespaceAnalysisUseCase,
    private val loadRiskAnalysisUseCase: LoadRiskAnalysisUseCase,
    private val loadSlowlogUseCase: LoadSlowlogUseCase,
    private val loadCommandStatsUseCase: LoadCommandStatsUseCase,
    private val loadLatencyStatsUseCase: LoadLatencyStatsUseCase,
    private val commandSuggestionEngine: CommandSuggestionEngine,
    private val renderer: MarkdownReportRenderer,
    private val writer: MarkdownReportFileWriter,
) {

    suspend fun export(dashboard: RedisDashboardSnapshot): ExportMarkdownReportResult {
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

        val markdown = renderer.render(reportSnapshot)
        val path = writer.write(
            markdown = markdown,
            generatedAt = generatedAt,
            profileName = dashboard.activeConnectionName,
        )
        return ExportMarkdownReportResult(
            path = path,
            generatedAt = generatedAt,
        )
    }
}
