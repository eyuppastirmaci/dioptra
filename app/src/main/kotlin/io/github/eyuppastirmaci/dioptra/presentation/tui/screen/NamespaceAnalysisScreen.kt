package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.format.ByteSizeFormatter
import io.github.eyuppastirmaci.dioptra.application.namespace.LoadNamespaceAnalysisUseCase
import io.github.eyuppastirmaci.dioptra.application.namespace.LoadNamespaceDetailUseCase
import io.github.eyuppastirmaci.dioptra.application.namespace.NamespaceBookmarkManager
import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceHealthLevel
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceSummary
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.namespace.NamespaceAnalysisSortMode
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.namespace.NamespaceAnalysisState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class NamespaceAnalysisScreen(
    private val loadNamespaceAnalysisUseCase: LoadNamespaceAnalysisUseCase,
    private val loadNamespaceDetailUseCase: LoadNamespaceDetailUseCase,
    private val namespaceAnalysisSettings: NamespaceAnalysisSettings,
    private val openSettings: (() -> TuiScreen)? = null,
    private val profileName: String = "",
    private val bookmarkManager: NamespaceBookmarkManager? = null,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private val logger = LoggerFactory.getLogger(NamespaceAnalysisScreen::class.java)
    private val byteSizeFormatter = ByteSizeFormatter()
    private val screenScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            DioptraCoroutineExceptionHandler.create(
                logger = logger,
                contextName = "NamespaceAnalysisScreen",
                onError = { exception ->
                    state = NamespaceAnalysisState.Error(UserFacingErrorMessage.from(exception))
                },
            ),
    )

    private var loadJob: Job? = null
    private var bookmarkedNamespaces: Set<String> = bookmarkManager?.load(profileName).orEmpty()
    private var bookmarkMessage: String? = null

    @Volatile
    private var state: NamespaceAnalysisState = NamespaceAnalysisState.Loading

    init {
        load()
    }

    override fun render(context: TuiContext) {
        context.clearBackground()
        drawPanel(context)
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isExitKey(keyStroke) -> TuiScreenResult.Exit
            isBackKey(keyStroke) -> TuiScreenResult.Navigate(back())
            keyStroke.keyType == KeyType.Enter -> openSelectedNamespace()
            isCharacter(keyStroke, 'r') -> {
                state = NamespaceAnalysisState.Loading
                load()
                TuiScreenResult.Continue
            }

            isCharacter(keyStroke, 's') -> {
                cycleSortMode()
                TuiScreenResult.Continue
            }

            isCharacter(keyStroke, 'a') && openSettings != null -> {
                TuiScreenResult.Navigate(openSettings.invoke())
            }

            isCharacter(keyStroke, 'b') -> {
                toggleSelectedBookmark()
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.ArrowDown || isCharacter(keyStroke, 'j') -> {
                scrollDown()
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.ArrowUp || isCharacter(keyStroke, 'k') -> {
                scrollUp()
                TuiScreenResult.Continue
            }

            else -> TuiScreenResult.Continue
        }
    }

    override fun close() {
        screenScope.cancel()
    }

    private fun openSelectedNamespace(): TuiScreenResult {
        val loaded = state as? NamespaceAnalysisState.Loaded ?: return TuiScreenResult.Continue
        val selectedSummary = loaded.selectedSummary ?: return TuiScreenResult.Continue
        return TuiScreenResult.Navigate(
            NamespaceDetailScreen(
                namespaceName = selectedSummary.identity.normalizedName,
                loadNamespaceDetailUseCase = loadNamespaceDetailUseCase,
                namespaceAnalysisSettings = namespaceAnalysisSettings,
                back = { this },
            )
        )
    }

    private fun load() {
        loadJob?.cancel()
        bookmarkMessage = null
        loadJob = screenScope.launch(Dispatchers.IO) {
            bookmarkedNamespaces = bookmarkManager?.load(profileName).orEmpty()
            state = NamespaceAnalysisState.Loaded(snapshot = loadNamespaceAnalysisUseCase.load())
        }
    }

    private fun toggleSelectedBookmark() {
        val manager = bookmarkManager ?: return
        val loaded = state as? NamespaceAnalysisState.Loaded ?: return
        val selectedSummary = loaded.selectedSummary ?: return
        val namespaceName = selectedSummary.identity.normalizedName
        val result = manager.toggle(
            profileName = profileName,
            namespaceName = namespaceName,
        )
        bookmarkedNamespaces = result.bookmarks
        bookmarkMessage = if (result.added) {
            "Bookmarked namespace: $namespaceName"
        } else {
            "Removed bookmark: $namespaceName"
        }
    }

    private fun cycleSortMode() {
        val loaded = state as? NamespaceAnalysisState.Loaded ?: return
        val nextMode = when (loaded.sortMode) {
            NamespaceAnalysisSortMode.RISK -> NamespaceAnalysisSortMode.KEY_COUNT
            NamespaceAnalysisSortMode.KEY_COUNT -> NamespaceAnalysisSortMode.MEMORY_CONCENTRATION
            NamespaceAnalysisSortMode.MEMORY_CONCENTRATION -> NamespaceAnalysisSortMode.TTL_COVERAGE
            NamespaceAnalysisSortMode.TTL_COVERAGE -> NamespaceAnalysisSortMode.NO_TTL
            NamespaceAnalysisSortMode.NO_TTL -> NamespaceAnalysisSortMode.RISK
        }
        state = loaded.copy(sortMode = nextMode, selectedIndex = 0, scrollOffset = 0)
    }

    private fun scrollDown() {
        val loaded = state as? NamespaceAnalysisState.Loaded ?: return
        val maxIndex = (loaded.sorted.size - 1).coerceAtLeast(0)
        val newIndex = (loaded.selectedIndex + 1).coerceAtMost(maxIndex)
        val newScroll = adjustScroll(newIndex, loaded.scrollOffset)
        state = loaded.copy(selectedIndex = newIndex, scrollOffset = newScroll)
    }

    private fun scrollUp() {
        val loaded = state as? NamespaceAnalysisState.Loaded ?: return
        val newIndex = (loaded.selectedIndex - 1).coerceAtLeast(0)
        val newScroll = adjustScroll(newIndex, loaded.scrollOffset)
        state = loaded.copy(selectedIndex = newIndex, scrollOffset = newScroll)
    }

    private fun adjustScroll(selectedIndex: Int, currentScroll: Int): Int {
        return when {
            selectedIndex < currentScroll -> selectedIndex
            selectedIndex >= currentScroll + VISIBLE_ROWS -> selectedIndex - VISIBLE_ROWS + 1
            else -> currentScroll
        }
    }

    private fun drawPanel(context: TuiContext) {
        val terminalWidth = context.screen.terminalSize.columns
        val panelWidth = (terminalWidth - 4).coerceIn(MIN_PANEL_WIDTH, MAX_PANEL_WIDTH)
        val panelRect = TuiRect(left = 2, top = 1, width = panelWidth, height = 22)
        Panel.draw(context = context, rect = panelRect)

        drawHeader(context, panelRect)

        when (val currentState = state) {
            is NamespaceAnalysisState.Loading -> drawLoading(context, panelRect)
            is NamespaceAnalysisState.Error -> drawError(context, panelRect, currentState.message)
            is NamespaceAnalysisState.Loaded -> drawSummaries(context, panelRect, currentState)
        }

        drawFooter(context, panelRect)
    }

    private fun drawHeader(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Namespace Analysis",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        val subtitle = when (val currentState = state) {
            is NamespaceAnalysisState.Loading -> "Scanning Redis keyspace..."
            is NamespaceAnalysisState.Error -> "Error loading namespace analysis"
            is NamespaceAnalysisState.Loaded -> {
                val warningSuffix = if (currentState.snapshot.analysisWarnings.isEmpty()) "" else "   ${currentState.snapshot.analysisWarnings.size} warnings"
                "Sorted by ${currentState.sortMode.label}   ${currentState.snapshot.totalNamespaceCount} namespaces   ${currentState.snapshot.analyzedKeyCount} keys   ign:${currentState.snapshot.ignoredKeyCount} sup:${currentState.snapshot.alertSuppressedKeyCount}$warningSuffix"
            }
        }

        context.putText(
            column = panel.left + 3,
            row = panel.top + 2,
            text = fit(subtitle, panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panel.left + 3,
            row = panel.top + 3,
            text = fit(settingsSummary(), panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawLoading(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 7,
            text = "Loading namespace analysis...",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawError(context: TuiContext, panel: TuiRect, message: String) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 7,
            text = fit("Error: $message", panel.width - 6),
            foregroundColor = context.theme.danger,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawSummaries(
        context: TuiContext,
        panel: TuiRect,
        loaded: NamespaceAnalysisState.Loaded,
    ) {
        if (loaded.snapshot.summaries.isEmpty()) {
            context.putText(
                column = panel.left + 3,
                row = panel.top + 7,
                text = "No namespaces discovered for the current scan.",
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            return
        }

        val headerRow = panel.top + 5
        context.putText(panel.left + COL_RANK, headerRow, "#", context.theme.label, context.theme.panel)
        context.putText(panel.left + COL_NAMESPACE, headerRow, "Namespace", context.theme.label, context.theme.panel)
        context.putText(panel.left + COL_KEYS, headerRow, "Keys", context.theme.label, context.theme.panel)
        context.putText(panel.left + COL_TTL, headerRow, "TTL%", context.theme.label, context.theme.panel)
        context.putText(panel.left + COL_NO_TTL, headerRow, "NoTTL", context.theme.label, context.theme.panel)
        context.putText(panel.left + COL_MEMORY, headerRow, "Mem%", context.theme.label, context.theme.panel)
        context.putText(panel.left + COL_SCORE, headerRow, "Score", context.theme.label, context.theme.panel)

        context.putText(
            column = panel.left + 2,
            row = panel.top + 6,
            text = "─".repeat(panel.width - 4),
            foregroundColor = context.theme.border,
            backgroundColor = context.theme.panel,
        )

        val visibleItems = loaded.sorted.drop(loaded.scrollOffset).take(VISIBLE_ROWS)
        visibleItems.forEachIndexed { relativeIndex, summary ->
            val absoluteIndex = loaded.scrollOffset + relativeIndex
            val row = panel.top + FIRST_ENTRY_ROW + relativeIndex
            val isSelected = absoluteIndex == loaded.selectedIndex
            val bg = if (isSelected) context.theme.border else context.theme.panel
            val fg = if (isSelected) context.theme.panel else context.theme.value
            val namespaceColor = if (isSelected) context.theme.panel else namespaceColor(context, summary)

            context.putText(panel.left + COL_RANK, row, (absoluteIndex + 1).toString().padEnd(3), fg, bg)
            context.putText(panel.left + COL_NAMESPACE, row, fit(formatNamespaceName(summary), COL_KEYS - COL_NAMESPACE - 1).padEnd(COL_KEYS - COL_NAMESPACE), namespaceColor, bg)
            context.putText(panel.left + COL_KEYS, row, summary.keyCount.toString().padStart(COL_TTL - COL_KEYS - 1), fg, bg)
            context.putText(panel.left + COL_TTL, row, formatPercent(summary.ttlCoverage.coveragePercent).padStart(COL_NO_TTL - COL_TTL - 1), fg, bg)
            context.putText(panel.left + COL_NO_TTL, row, summary.noTtlKeyCount.toString().padStart(COL_MEMORY - COL_NO_TTL - 1), fg, bg)
            context.putText(panel.left + COL_MEMORY, row, formatPercent(summary.memoryConcentrationPercent).padStart(COL_SCORE - COL_MEMORY - 1), fg, bg)
            context.putText(panel.left + COL_SCORE, row, summary.health.score.toString().padStart(5), fg, bg)
        }

        drawSelectedSummary(context, panel, loaded.selectedSummary, loaded)
    }

    private fun drawSelectedSummary(
        context: TuiContext,
        panel: TuiRect,
        summary: NamespaceSummary?,
        loaded: NamespaceAnalysisState.Loaded,
    ) {
        context.putText(
            column = panel.left + 2,
            row = panel.top + DETAIL_SEPARATOR_ROW,
            text = "─".repeat(panel.width - 4),
            foregroundColor = context.theme.border,
            backgroundColor = context.theme.panel,
        )

        if (summary == null) {
            return
        }

        val avgTtl = summary.averageTtlSeconds?.let(::formatTtl) ?: "n/a"
        val memory = formatMemory(summary.estimatedMemoryUsage)
        val riskSummary = if (summary.riskReasons.isEmpty()) {
            "none"
        } else {
            summary.riskReasons.joinToString(limit = 3, truncated = "...") { it.name.lowercase() }
        }
        val topRiskySummary = summarizeNamespaces(loaded.snapshot.topRiskyNamespaces.map { it.identity.displayName })
        val unexpectedSummary = summarizeNamespaces(loaded.snapshot.unexpectedNamespaces.map { it.namespaceName })
        val anomalySummary = summarizeNamespaces(loaded.snapshot.anomalousNamespaces.map { it.identity.displayName })

        context.putText(
            column = panel.left + 3,
            row = panel.top + DETAIL_FIRST_ROW,
            text = fit(
                "Selected: ${formatNamespaceName(summary)}   avg ttl: $avgTtl   mem: $memory",
                panel.width - 6,
            ),
            foregroundColor = context.theme.value,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + DETAIL_SECOND_ROW,
            text = fit(
                "Health: ${summary.health.level.name.lowercase()} (${summary.health.score})   risks: $riskSummary",
                panel.width - 6,
            ),
            foregroundColor = healthColor(context, summary.health.level),
            backgroundColor = context.theme.panel,
        )
        val message = bookmarkMessage
        if (message != null) {
            context.putText(
                column = panel.left + 3,
                row = panel.top + DETAIL_THIRD_ROW,
                text = fit(message, panel.width - 6),
                foregroundColor = context.theme.success,
                backgroundColor = context.theme.panel,
            )
            return
        }

        context.putText(
            column = panel.left + 3,
            row = panel.top + DETAIL_THIRD_ROW,
            text = fit(
                "Top risky: $topRiskySummary   unexpected: $unexpectedSummary   anomalies: $anomalySummary   ign:${loaded.snapshot.ignoredKeyCount} sup:${loaded.snapshot.alertSuppressedKeyCount}",
                panel.width - 6,
            ),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawFooter(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + panel.height - 2,
            text = fit("j/k:move enter:detail b:mark s:sort a:aset r:refresh esc:back q:exit", panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun formatNamespaceName(summary: NamespaceSummary): String {
        val marker = if (summary.identity.normalizedName in bookmarkedNamespaces) "* " else ""
        return marker + summary.identity.displayName
    }

    private fun namespaceColor(context: TuiContext, summary: NamespaceSummary) = when {
        summary.unexpectedNamespaceSignal != null -> context.theme.warning
        summary.namingAnomalies.isNotEmpty() -> context.theme.danger
        else -> context.theme.value
    }

    private fun healthColor(context: TuiContext, level: NamespaceHealthLevel) = when (level) {
        NamespaceHealthLevel.HEALTHY -> context.theme.success
        NamespaceHealthLevel.WATCH -> context.theme.warning
        NamespaceHealthLevel.RISKY -> context.theme.danger
        NamespaceHealthLevel.CRITICAL -> context.theme.danger
    }

    private fun formatPercent(value: Double): String {
        return "${value.toInt()}%"
    }

    private fun formatMemory(memoryUsage: NamespaceMemoryUsage): String {
        return when (memoryUsage) {
            NamespaceMemoryUsage.Unknown -> "unknown"
            is NamespaceMemoryUsage.Estimated -> {
                val suffix = memoryUsage.sampledKeys?.let { " sample:$it" } ?: ""
                byteSizeFormatter.format(memoryUsage.totalBytes) + suffix
            }
        }
    }

    private fun formatTtl(seconds: Long): String {
        return when {
            seconds < 60L -> "${seconds}s"
            seconds < 3_600L -> "${seconds / 60}m"
            seconds < 86_400L -> "${seconds / 3_600}h"
            else -> "${seconds / 86_400}d"
        }
    }

    private fun fit(text: String, maxWidth: Int): String {
        return text.take(maxWidth.coerceAtLeast(0))
    }

    private fun summarizeNamespaces(names: List<String>): String {
        if (names.isEmpty()) {
            return "none"
        }

        return names.distinct().take(2).joinToString(separator = ",", truncated = "...")
    }

    private fun settingsSummary(): String {
        val delimiters = namespaceAnalysisSettings.normalizedDelimiters.joinToString(separator = ",")
        val expectedCount = namespaceAnalysisSettings.normalizedExpectedNamespaces.size
        val allowedCount = namespaceAnalysisSettings.normalizedAllowedKeyPatterns.size
        val ignoredCount = namespaceAnalysisSettings.normalizedIgnoredKeyPatterns.size
        return "delim:$delimiters depth:${namespaceAnalysisSettings.normalizedNamespaceDepth} expected:$expectedCount allow:$allowedCount(suppress) ignore:$ignoredCount(exclude) ws:${flag(namespaceAnalysisSettings.allowWhitespaceInKeys)} upper:${flag(namespaceAnalysisSettings.allowUppercaseInKeys)} repeat:${flag(namespaceAnalysisSettings.allowRepeatedDelimiters)}"
    }

    private fun flag(value: Boolean): String {
        return if (value) "allow" else "flag"
    }

    private fun isExitKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.EOF || isCharacter(keyStroke, 'q')
    }

    private fun isBackKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.Escape
    }

    private fun isCharacter(keyStroke: KeyStroke, expected: Char): Boolean {
        return keyStroke.keyType == KeyType.Character &&
            keyStroke.character?.lowercaseChar() == expected
    }

    private companion object {
        const val VISIBLE_ROWS = 8
        const val FIRST_ENTRY_ROW = 7
        const val DETAIL_SEPARATOR_ROW = 15
        const val DETAIL_FIRST_ROW = 16
        const val DETAIL_SECOND_ROW = 17
        const val DETAIL_THIRD_ROW = 18
        const val COL_RANK = 3
        const val COL_NAMESPACE = 7
        const val COL_KEYS = 34
        const val COL_TTL = 43
        const val COL_NO_TTL = 50
        const val COL_MEMORY = 59
        const val COL_SCORE = 67
        const val MIN_PANEL_WIDTH = 76
        const val MAX_PANEL_WIDTH = 118
    }
}
