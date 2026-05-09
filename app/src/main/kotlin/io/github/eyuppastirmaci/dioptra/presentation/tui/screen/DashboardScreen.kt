package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysUseCase
import io.github.eyuppastirmaci.dioptra.application.report.ExportMarkdownReportUseCase
import io.github.eyuppastirmaci.dioptra.application.session.SessionTracker
import io.github.eyuppastirmaci.dioptra.application.session.TrackedScreen
import io.github.eyuppastirmaci.dioptra.application.snapshot.DiffAnalysisSnapshotsUseCase
import io.github.eyuppastirmaci.dioptra.application.snapshot.SaveAnalysisSnapshotUseCase
import io.github.eyuppastirmaci.dioptra.application.suggestion.CommandSuggestionEngine
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyUseCase
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyValueUseCase
import io.github.eyuppastirmaci.dioptra.application.key.ExpireKeyUseCase
import io.github.eyuppastirmaci.dioptra.application.key.LoadKeyDetailUseCase
import io.github.eyuppastirmaci.dioptra.application.safety.OperationAuditLogger
import io.github.eyuppastirmaci.dioptra.application.safety.ProtectedNamespaceRules
import io.github.eyuppastirmaci.dioptra.application.commandstats.LoadCommandStatsUseCase
import io.github.eyuppastirmaci.dioptra.application.latency.LoadLatencyStatsUseCase
import io.github.eyuppastirmaci.dioptra.application.namespace.LoadNamespaceAnalysisUseCase
import io.github.eyuppastirmaci.dioptra.application.namespace.LoadNamespaceDetailUseCase
import io.github.eyuppastirmaci.dioptra.application.namespace.NamespaceBookmarkManager
import io.github.eyuppastirmaci.dioptra.application.namespace.SaveNamespaceAnalysisSettingsUseCase
import io.github.eyuppastirmaci.dioptra.application.profile.ProfileImportExportUseCase
import io.github.eyuppastirmaci.dioptra.application.profile.TeamProfileTemplateUseCase
import io.github.eyuppastirmaci.dioptra.application.risk.LoadRiskAnalysisUseCase
import io.github.eyuppastirmaci.dioptra.application.slowlog.LoadSlowlogUseCase
import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.MetricRow
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeyBrowserSorter
import io.github.eyuppastirmaci.dioptra.presentation.tui.input.TuiKeyMatcher
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.commandpalette.CommandPaletteItem
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser.KeyBrowserRenderer
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.snapshot.SnapshotDiffMode

class DashboardScreen(
    private val snapshot: RedisDashboardSnapshot,
    private val browseKeysUseCase: BrowseKeysUseCase,
    private val loadKeyDetailUseCase: LoadKeyDetailUseCase,
    private val expireKeyUseCase: ExpireKeyUseCase,
    private val deleteKeyUseCase: DeleteKeyUseCase,
    private val deleteKeyValueUseCase: DeleteKeyValueUseCase,
    private val loadSlowlogUseCase: LoadSlowlogUseCase,
    private val loadCommandStatsUseCase: LoadCommandStatsUseCase,
    private val loadLatencyStatsUseCase: LoadLatencyStatsUseCase,
    private val loadRiskAnalysisUseCase: LoadRiskAnalysisUseCase,
    private val namespaceAnalysisUseCaseFactory: (NamespaceAnalysisSettings) -> Pair<LoadNamespaceAnalysisUseCase, LoadNamespaceDetailUseCase>,
    namespaceAnalysisSettings: NamespaceAnalysisSettings,
    private val saveNamespaceAnalysisSettingsUseCase: SaveNamespaceAnalysisSettingsUseCase,
    private val readOnly: Boolean,
    private val productionSafety: Boolean,
    private val protectedNamespaceRules: ProtectedNamespaceRules,
    private val operationAuditLogger: OperationAuditLogger,
    private val keyBrowserRenderer: KeyBrowserRenderer,
    private val keyBrowserSorter: RedisKeyBrowserSorter,
    private val keyMatcher: TuiKeyMatcher,
    private val disconnect: (() -> TuiScreen)? = null,
    private val sessionTracker: SessionTracker? = null,
    private val commandSuggestionEngine: CommandSuggestionEngine? = null,
    private val markdownReportExportUseCaseFactory: ((NamespaceAnalysisSettings) -> ExportMarkdownReportUseCase)? = null,
    private val analysisSnapshotSaveUseCaseFactory: ((NamespaceAnalysisSettings) -> SaveAnalysisSnapshotUseCase)? = null,
    private val diffAnalysisSnapshotsUseCase: DiffAnalysisSnapshotsUseCase? = null,
    private val namespaceBookmarkManager: NamespaceBookmarkManager? = null,
    private val profileImportExportUseCase: ProfileImportExportUseCase? = null,
    private val teamProfileTemplateUseCase: TeamProfileTemplateUseCase? = null,
) : TuiScreen {

    private var namespaceAnalysisSettings = namespaceAnalysisSettings

    /**
     * Renders the Redis dashboard layout using the active TUI context.
     */
    override fun render(context: TuiContext) {
        drawDashboardPanel(context)
    }

    /**
     * Handles dashboard-level keyboard shortcuts.
     */
    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isExitKey(keyStroke) -> TuiScreenResult.Exit

            isCharacter(keyStroke, 'p') -> openCommandPalette()
            isCharacter(keyStroke, 'k') -> openKeyBrowser()
            isCharacter(keyStroke, 's') -> openSlowlog()
            isCharacter(keyStroke, 'c') -> openCommandStats()
            isCharacter(keyStroke, 'l') -> openLatencyStats()
            isCharacter(keyStroke, 'r') -> openRiskAnalysis()
            isCharacter(keyStroke, 'n') -> openNamespaceAnalysis()
            isCharacter(keyStroke, 'm') && namespaceBookmarkManager != null -> openBookmarkedNamespaces()
            isCharacter(keyStroke, 'g') && commandSuggestionEngine != null -> openCommandSuggestions()
            isCharacter(keyStroke, 'e') && markdownReportExportUseCaseFactory != null -> exportMarkdownReport()
            isCharacter(keyStroke, 'x') && analysisSnapshotSaveUseCaseFactory != null -> saveAnalysisSnapshot()
            isCharacter(keyStroke, 'f') && diffAnalysisSnapshotsUseCase != null -> diffAnalysisSnapshots()
            isCharacter(keyStroke, 'u') && diffAnalysisSnapshotsUseCase != null -> compareCleanupSnapshots()
            isCharacter(keyStroke, 'i') && profileImportExportUseCase != null -> openProfileImportExport()
            isCharacter(keyStroke, 't') && teamProfileTemplateUseCase != null -> openTeamProfileTemplates()
            isCharacter(keyStroke, 'a') -> openNamespaceSettings()
            isCharacter(keyStroke, 'd') && disconnect != null -> disconnectFromDashboard()

            else -> TuiScreenResult.Continue
        }
    }

    private fun openCommandPalette(): TuiScreenResult {
        return TuiScreenResult.Navigate(
            nextScreen = CommandPaletteScreen(
                items = commandPaletteItems(),
                back = { this },
            )
        )
    }

    private fun openKeyBrowser(): TuiScreenResult {
        sessionTracker?.recordScreenVisit(TrackedScreen.KEY_BROWSER)
        return TuiScreenResult.Navigate(
            nextScreen = KeyBrowserScreen(
                browseKeysUseCase = browseKeysUseCase,
                loadKeyDetailUseCase = loadKeyDetailUseCase,
                expireKeyUseCase = expireKeyUseCase,
                deleteKeyUseCase = deleteKeyUseCase,
                deleteKeyValueUseCase = deleteKeyValueUseCase,
                readOnly = readOnly,
                productionSafety = productionSafety,
                protectedNamespaceRules = protectedNamespaceRules,
                operationAuditLogger = operationAuditLogger,
                renderer = keyBrowserRenderer,
                sorter = keyBrowserSorter,
                keyMatcher = keyMatcher,
                back = { this },
                onKeysBrowsed = { count -> sessionTracker?.recordKeysBrowsed(count) },
                onKeyInspected = { sessionTracker?.recordKeyInspected() },
            )
        )
    }

    private fun openSlowlog(): TuiScreenResult {
        sessionTracker?.recordScreenVisit(TrackedScreen.SLOWLOG)
        return TuiScreenResult.Navigate(
            nextScreen = SlowlogScreen(
                loadSlowlogUseCase = loadSlowlogUseCase,
                back = { this },
            )
        )
    }

    private fun openCommandStats(): TuiScreenResult {
        sessionTracker?.recordScreenVisit(TrackedScreen.COMMAND_STATS)
        return TuiScreenResult.Navigate(
            nextScreen = CommandStatsScreen(
                loadCommandStatsUseCase = loadCommandStatsUseCase,
                back = { this },
            )
        )
    }

    private fun openLatencyStats(): TuiScreenResult {
        sessionTracker?.recordScreenVisit(TrackedScreen.LATENCY_STATS)
        return TuiScreenResult.Navigate(
            nextScreen = LatencyStatsScreen(
                loadLatencyStatsUseCase = loadLatencyStatsUseCase,
                back = { this },
            )
        )
    }

    private fun openRiskAnalysis(): TuiScreenResult {
        sessionTracker?.recordScreenVisit(TrackedScreen.RISK_ANALYSIS)
        return TuiScreenResult.Navigate(
            nextScreen = RiskAnalysisScreen(
                loadRiskAnalysisUseCase = loadRiskAnalysisUseCase,
                back = { this },
            )
        )
    }

    private fun openNamespaceAnalysis(): TuiScreenResult {
        sessionTracker?.recordScreenVisit(TrackedScreen.NAMESPACE_ANALYSIS)
        val (loadNamespaceAnalysisUseCase, loadNamespaceDetailUseCase) = namespaceAnalysisUseCaseFactory(namespaceAnalysisSettings)
        return TuiScreenResult.Navigate(
            nextScreen = NamespaceAnalysisScreen(
                loadNamespaceAnalysisUseCase = loadNamespaceAnalysisUseCase,
                loadNamespaceDetailUseCase = loadNamespaceDetailUseCase,
                namespaceAnalysisSettings = namespaceAnalysisSettings,
                openSettings = { createNamespaceSettingsScreen() },
                profileName = snapshot.activeConnectionName,
                bookmarkManager = namespaceBookmarkManager,
                back = { this },
            )
        )
    }

    private fun openBookmarkedNamespaces(): TuiScreenResult {
        val bookmarkManager = namespaceBookmarkManager ?: return TuiScreenResult.Continue
        val (_, loadNamespaceDetailUseCase) = namespaceAnalysisUseCaseFactory(namespaceAnalysisSettings)
        return TuiScreenResult.Navigate(
            nextScreen = BookmarkedNamespacesScreen(
                profileName = snapshot.activeConnectionName,
                bookmarkManager = bookmarkManager,
                loadNamespaceDetailUseCase = loadNamespaceDetailUseCase,
                namespaceAnalysisSettings = namespaceAnalysisSettings,
                back = { this },
            )
        )
    }

    private fun openCommandSuggestions(): TuiScreenResult {
        val engine = commandSuggestionEngine ?: return TuiScreenResult.Continue
        return TuiScreenResult.Navigate(
            nextScreen = CommandSuggestionsScreen(
                dashboardSnapshot = snapshot,
                loadRiskAnalysisUseCase = loadRiskAnalysisUseCase,
                engine = engine,
                back = { this },
            )
        )
    }

    private fun exportMarkdownReport(): TuiScreenResult {
        val exportUseCaseFactory = markdownReportExportUseCaseFactory ?: return TuiScreenResult.Continue
        return TuiScreenResult.Navigate(
            nextScreen = MarkdownReportExportScreen(
                dashboardSnapshot = snapshot,
                exportMarkdownReportUseCase = exportUseCaseFactory.invoke(namespaceAnalysisSettings),
                back = { this },
            )
        )
    }

    private fun saveAnalysisSnapshot(): TuiScreenResult {
        val saveUseCaseFactory = analysisSnapshotSaveUseCaseFactory ?: return TuiScreenResult.Continue
        return TuiScreenResult.Navigate(
            nextScreen = SaveAnalysisSnapshotScreen(
                dashboardSnapshot = snapshot,
                saveAnalysisSnapshotUseCase = saveUseCaseFactory.invoke(namespaceAnalysisSettings),
                back = { this },
            )
        )
    }

    private fun diffAnalysisSnapshots(): TuiScreenResult {
        val diffUseCase = diffAnalysisSnapshotsUseCase ?: return TuiScreenResult.Continue
        return TuiScreenResult.Navigate(
            nextScreen = SnapshotDiffScreen(
                diffAnalysisSnapshotsUseCase = diffUseCase,
                back = { this },
            )
        )
    }

    private fun compareCleanupSnapshots(): TuiScreenResult {
        val diffUseCase = diffAnalysisSnapshotsUseCase ?: return TuiScreenResult.Continue
        return TuiScreenResult.Navigate(
            nextScreen = SnapshotDiffScreen(
                diffAnalysisSnapshotsUseCase = diffUseCase,
                mode = SnapshotDiffMode.CLEANUP,
                back = { this },
            )
        )
    }

    private fun openProfileImportExport(): TuiScreenResult {
        val useCase = profileImportExportUseCase ?: return TuiScreenResult.Continue
        return TuiScreenResult.Navigate(
            nextScreen = ProfileImportExportScreen(
                useCase = useCase,
                back = { this },
            )
        )
    }

    private fun openTeamProfileTemplates(): TuiScreenResult {
        val useCase = teamProfileTemplateUseCase ?: return TuiScreenResult.Continue
        return TuiScreenResult.Navigate(
            nextScreen = TeamProfileTemplatesScreen(
                useCase = useCase,
                back = { this },
            )
        )
    }

    private fun openNamespaceSettings(): TuiScreenResult {
        return TuiScreenResult.Navigate(
            nextScreen = createNamespaceSettingsScreen(),
        )
    }

    private fun disconnectFromDashboard(): TuiScreenResult {
        val nextScreen = disconnect?.invoke() ?: return TuiScreenResult.Continue
        return TuiScreenResult.Navigate(nextScreen = nextScreen)
    }

    private fun commandPaletteItems(): List<CommandPaletteItem> {
        return listOfNotNull(
            CommandPaletteItem(
                title = "Open Key Browser",
                description = "Scan keys, inspect TTL/type/memory",
                shortcut = "k",
                keywords = listOf("keys", "scan", "browser"),
                action = ::openKeyBrowser,
            ),
            CommandPaletteItem(
                title = "Open Slowlog",
                description = "View slow Redis commands",
                shortcut = "s",
                keywords = listOf("slow", "debug", "performance"),
                action = ::openSlowlog,
            ),
            CommandPaletteItem(
                title = "Open Command Stats",
                description = "Inspect INFO commandstats",
                shortcut = "c",
                keywords = listOf("cmd", "stats", "calls"),
                action = ::openCommandStats,
            ),
            CommandPaletteItem(
                title = "Open Latency Stats",
                description = "Inspect Redis latency percentiles",
                shortcut = "l",
                keywords = listOf("latency", "p99", "performance"),
                action = ::openLatencyStats,
            ),
            CommandPaletteItem(
                title = "Open Risk Analysis",
                description = "Find big and no-TTL keys",
                shortcut = "r",
                keywords = listOf("risk", "big", "ttl"),
                action = ::openRiskAnalysis,
            ),
            CommandPaletteItem(
                title = "Open Namespace Analysis",
                description = "Analyze key namespaces",
                shortcut = "n",
                keywords = listOf("namespace", "prefix", "health"),
                action = ::openNamespaceAnalysis,
            ),
            namespaceBookmarkManager?.let {
                CommandPaletteItem(
                    title = "Open Bookmarked Namespaces",
                    description = "Jump to saved namespaces",
                    shortcut = "m",
                    keywords = listOf("bookmarks", "marked", "namespace"),
                    action = ::openBookmarkedNamespaces,
                )
            },
            commandSuggestionEngine?.let {
                CommandPaletteItem(
                    title = "Open Command Suggestions",
                    description = "Show redis-cli suggestions",
                    shortcut = "g",
                    keywords = listOf("suggestions", "redis-cli", "commands"),
                    action = ::openCommandSuggestions,
                )
            },
            markdownReportExportUseCaseFactory?.let {
                CommandPaletteItem(
                    title = "Export Markdown Report",
                    description = "Write deterministic report",
                    shortcut = "e",
                    keywords = listOf("markdown", "report", "export"),
                    action = ::exportMarkdownReport,
                )
            },
            analysisSnapshotSaveUseCaseFactory?.let {
                CommandPaletteItem(
                    title = "Save Analysis Snapshot",
                    description = "Write versioned JSON snapshot",
                    shortcut = "x",
                    keywords = listOf("snapshot", "json", "diff", "save"),
                    action = ::saveAnalysisSnapshot,
                )
            },
            diffAnalysisSnapshotsUseCase?.let {
                CommandPaletteItem(
                    title = "Diff Analysis Snapshots",
                    description = "Compare two saved snapshots",
                    shortcut = "f",
                    keywords = listOf("snapshot", "diff", "compare"),
                    action = ::diffAnalysisSnapshots,
                )
            },
            diffAnalysisSnapshotsUseCase?.let {
                CommandPaletteItem(
                    title = "Compare Before/After Cleanup",
                    description = "Review cleanup impact",
                    shortcut = "u",
                    keywords = listOf("cleanup", "before", "after", "snapshot", "compare"),
                    action = ::compareCleanupSnapshots,
                )
            },
            profileImportExportUseCase?.let {
                CommandPaletteItem(
                    title = "Profile Import/Export",
                    description = "Move saved connection profiles",
                    shortcut = "i",
                    keywords = listOf("profile", "import", "export", "connection"),
                    action = ::openProfileImportExport,
                )
            },
            teamProfileTemplateUseCase?.let {
                CommandPaletteItem(
                    title = "Team Profile Templates",
                    description = "Share active profile as a template",
                    shortcut = "t",
                    keywords = listOf("team", "template", "profile", "share"),
                    action = ::openTeamProfileTemplates,
                )
            },
            CommandPaletteItem(
                title = "Namespace Settings",
                description = "Configure namespace analysis",
                shortcut = "a",
                keywords = listOf("settings", "analysis", "namespace"),
                action = ::openNamespaceSettings,
            ),
            disconnect?.let {
                CommandPaletteItem(
                    title = "Disconnect",
                    description = "Close Redis connection",
                    shortcut = "d",
                    keywords = listOf("connection", "profile"),
                    action = ::disconnectFromDashboard,
                )
            },
            CommandPaletteItem(
                title = "Exit Dioptra",
                description = "Close the TUI",
                shortcut = "q",
                keywords = listOf("quit", "close"),
                action = { TuiScreenResult.Exit },
            ),
        )
    }

    private fun drawDashboardPanel(context: TuiContext) {
        val panelRect = TuiRect(
            left = 4,
            top = 2,
            width = 72,
            height = 22,
        )

        Panel.draw(
            context = context,
            rect = panelRect,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 1,
            text = "Dioptra Redis Dashboard",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 3,
            text = "Live Redis instance overview",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 5,
            label = "Status",
            value = snapshot.status,
            valueForegroundColor = if (snapshot.status == "Connected") context.theme.success else context.theme.value,
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 5,
            label = "Profile",
            value = snapshot.activeConnectionName,
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 6,
            label = "Database",
            value = snapshot.selectedDatabase.toString(),
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 6,
            label = "Server",
            value = snapshot.redisVersion,
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 7,
            label = "Uptime",
            value = snapshot.uptime,
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 7,
            label = "Used memory",
            value = snapshot.usedMemoryHuman,
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 8,
            label = "Mem frag",
            value = snapshot.memoryFragmentationHint,
            valueForegroundColor = if (snapshot.memoryFragmentationHealthy) context.theme.value else context.theme.warning,
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 8,
            label = "Clients",
            value = "${snapshot.connectedClients} ${snapshot.connectedClientsWarning}",
            valueForegroundColor = if (snapshot.connectedClientsHealthy) context.theme.value else context.theme.warning,
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 9,
            label = "Blocked",
            value = snapshot.blockedClients.toString(),
            valueForegroundColor = if (snapshot.blockedClients == 0) context.theme.value else context.theme.warning,
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 9,
            label = "Ops/sec",
            value = snapshot.instantaneousOpsPerSecond.toString(),
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 10,
            label = "Commands",
            value = snapshot.totalCommandsProcessed.toString(),
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 10,
            label = "Total keys",
            value = snapshot.totalKeys.toString(),
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 11,
            label = "Key hits",
            value = snapshot.keyspaceHits.toString(),
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 11,
            label = "Key misses",
            value = snapshot.keyspaceMisses.toString(),
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 12,
            label = "Maxmemory",
            value = snapshot.maxmemoryPolicy,
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 12,
            label = "Evicted",
            value = snapshot.evictedKeys.toString(),
            valueForegroundColor = if (snapshot.evictedKeys == 0L) context.theme.value else context.theme.warning,
        )

        // Row 13 — persistence separator
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 13,
            text = "─── Persistence " + "─".repeat(50),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        // Row 14 — RDB status (left) + AOF status (right)
        val rdbLabel = if (snapshot.rdbBgsaveInProgress) "RDB saving…" else "RDB"
        drawLeftMetric(
            context = context,
            row = panelRect.top + 14,
            label = rdbLabel,
            value = snapshot.rdbStatus,
            valueForegroundColor = if (snapshot.rdbStatusHealthy) context.theme.value else context.theme.danger,
        )
        val aofValue = snapshot.aofStatus
        val aofColor = when {
            !snapshot.aofEnabled -> context.theme.hint
            snapshot.aofStatusHealthy -> context.theme.value
            else -> context.theme.danger
        }
        drawRightMetric(
            context = context,
            row = panelRect.top + 14,
            label = "AOF",
            value = aofValue,
            valueForegroundColor = aofColor,
        )

        // Row 15 — last save age (left) + unsaved changes (right)
        drawLeftMetric(
            context = context,
            row = panelRect.top + 15,
            label = "Last Save",
            value = snapshot.rdbLastSaveAge,
        )
        drawRightMetric(
            context = context,
            row = panelRect.top + 15,
            label = "Unsaved",
            value = snapshot.rdbChangesSinceLastSave.toString(),
            valueForegroundColor = if (snapshot.rdbChangesSinceLastSave == 0L) context.theme.value else context.theme.warning,
        )

        // Row 16 — replication separator
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 16,
            text = "─── Replication " + "─".repeat(50),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        // Row 17 — role (left) + replica count or link status (right)
        drawLeftMetric(
            context = context,
            row = panelRect.top + 17,
            label = "Role",
            value = snapshot.replicationRole,
        )
        if (snapshot.replicationRole == "slave") {
            val linkColor = if (snapshot.masterLinkHealthy) context.theme.value else context.theme.danger
            drawRightMetric(
                context = context,
                row = panelRect.top + 17,
                label = "Link",
                value = snapshot.masterLinkStatus,
                valueForegroundColor = linkColor,
            )
        } else {
            drawRightMetric(
                context = context,
                row = panelRect.top + 17,
                label = "Replicas",
                value = snapshot.connectedReplicas.toString(),
            )
        }

        footerLines().forEachIndexed { index, line ->
            context.putText(
                column = panelRect.left + 3,
                row = panelRect.top + panelRect.height - 3 + index,
                text = fitToPanelWidth(panelRect, line),
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
        }
    }

    private fun drawLeftMetric(
        context: TuiContext,
        row: Int,
        label: String,
        value: String,
        valueForegroundColor: TextColor = context.theme.value,
    ) {
        MetricRow.draw(
            context = context,
            row = row,
            label = label,
            value = compactMetricValue(value),
            labelColumn = 7,
            valueColumn = 20,
            labelWidth = 11,
            valueForegroundColor = valueForegroundColor,
        )
    }

    private fun drawRightMetric(
        context: TuiContext,
        row: Int,
        label: String,
        value: String,
        valueForegroundColor: TextColor = context.theme.value,
    ) {
        MetricRow.draw(
            context = context,
            row = row,
            label = label,
            value = compactMetricValue(value),
            labelColumn = 42,
            valueColumn = 55,
            labelWidth = 11,
            valueForegroundColor = valueForegroundColor,
        )
    }

    private fun compactMetricValue(value: String): String {
        return if (value.length <= METRIC_VALUE_WIDTH) {
            value
        } else {
            "${value.take(METRIC_VALUE_WIDTH - 1)}~"
        }
    }

    private fun fitToPanelWidth(panel: TuiRect, text: String): String {
        val maxWidth = (panel.width - 6).coerceAtLeast(0)
        return text.take(maxWidth)
    }

    private fun footerLines(): List<String> {
        val mode = buildString {
            if (readOnly) append("  ro")
            if (productionSafety) append("  safe")
        }
        val protected = if (protectedNamespaceRules.count > 0) {
            "  prot:${protectedNamespaceRules.count}"
        } else {
            ""
        }
        val firstLine = "p:pal k:key s:slow c:cmd l:lat r:risk n:ns m:marks g:sugg"
        val secondLine = if (disconnect == null) {
            "e:report x:snap f:diff u:clean i:prof t:tpl a:set q:exit$mode$protected"
        } else {
            "e:report x:snap f:diff u:clean i:prof t:tpl a:set d:disc q:exit$mode$protected"
        }
        return listOf(firstLine, secondLine)
    }

    private fun createNamespaceSettingsScreen(): TuiScreen {
        return NamespaceAnalysisSettingsScreen(
            initialSettings = namespaceAnalysisSettings,
            saveSettings = saveNamespaceAnalysisSettingsUseCase::save,
            saveAvailable = saveNamespaceAnalysisSettingsUseCase.canPersist(),
            unavailableMessage = saveNamespaceAnalysisSettingsUseCase.unavailableReason(),
            onSaved = { savedSettings ->
                namespaceAnalysisSettings = savedSettings
            },
            back = { this },
        )
    }

    private fun isExitKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.Escape ||
                keyStroke.keyType == KeyType.EOF ||
                isCharacter(keyStroke, 'q')
    }

    private fun isCharacter(
        keyStroke: KeyStroke,
        expectedCharacter: Char,
    ): Boolean {
        return keyStroke.keyType == KeyType.Character &&
                keyStroke.character?.lowercaseChar() == expectedCharacter
    }

    private companion object {
        const val METRIC_VALUE_WIDTH = 20
    }
}
