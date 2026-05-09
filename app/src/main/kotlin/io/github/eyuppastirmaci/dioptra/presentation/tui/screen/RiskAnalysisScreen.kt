package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.risk.LoadRiskAnalysisUseCase
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskKeyFinding
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskLevel
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskPatternFinding
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.ByteSizeFormatter
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.risk.RiskAnalysisSortMode
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.risk.RiskAnalysisState
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.risk.RiskAnalysisViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class RiskAnalysisScreen(
    private val loadRiskAnalysisUseCase: LoadRiskAnalysisUseCase,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private val logger = LoggerFactory.getLogger(RiskAnalysisScreen::class.java)
    private val byteSizeFormatter = ByteSizeFormatter()
    private val screenScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            DioptraCoroutineExceptionHandler.create(
                logger = logger,
                contextName = "RiskAnalysisScreen",
                onError = { exception ->
                    state = RiskAnalysisState.Error(UserFacingErrorMessage.from(exception))
                },
            ),
    )

    private var loadJob: Job? = null

    @Volatile
    private var state: RiskAnalysisState = RiskAnalysisState.Loading

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

            isCharacter(keyStroke, 'r') -> {
                state = RiskAnalysisState.Loading
                load()
                TuiScreenResult.Continue
            }

            isViewKey(keyStroke) -> {
                cycleViewMode()
                TuiScreenResult.Continue
            }

            isCharacter(keyStroke, 's') -> {
                cycleSortMode()
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

    private fun load() {
        loadJob?.cancel()
        loadJob = screenScope.launch(Dispatchers.IO) {
            state = RiskAnalysisState.Loaded(snapshot = loadRiskAnalysisUseCase.load())
        }
    }

    private fun cycleViewMode() {
        val loaded = state as? RiskAnalysisState.Loaded ?: return
        val nextMode = when (loaded.viewMode) {
            RiskAnalysisViewMode.OVERVIEW -> RiskAnalysisViewMode.BIG_KEYS
            RiskAnalysisViewMode.BIG_KEYS -> RiskAnalysisViewMode.NO_TTL
            RiskAnalysisViewMode.NO_TTL -> RiskAnalysisViewMode.PATTERNS
            RiskAnalysisViewMode.PATTERNS -> RiskAnalysisViewMode.OVERVIEW
        }
        state = loaded.copy(viewMode = nextMode, selectedIndex = 0, scrollOffset = 0)
    }

    private fun cycleSortMode() {
        val loaded = state as? RiskAnalysisState.Loaded ?: return
        val nextMode = when (loaded.sortMode) {
            RiskAnalysisSortMode.RISK -> RiskAnalysisSortMode.MEMORY
            RiskAnalysisSortMode.MEMORY -> RiskAnalysisSortMode.COUNT
            RiskAnalysisSortMode.COUNT -> RiskAnalysisSortMode.NAME
            RiskAnalysisSortMode.NAME -> RiskAnalysisSortMode.RISK
        }
        state = loaded.copy(sortMode = nextMode, selectedIndex = 0, scrollOffset = 0)
    }

    private fun scrollDown() {
        val loaded = state as? RiskAnalysisState.Loaded ?: return
        val maxIndex = (loaded.selectableCount - 1).coerceAtLeast(0)
        val newIndex = (loaded.selectedIndex + 1).coerceAtMost(maxIndex)
        state = loaded.copy(selectedIndex = newIndex, scrollOffset = adjustScroll(newIndex, loaded.scrollOffset))
    }

    private fun scrollUp() {
        val loaded = state as? RiskAnalysisState.Loaded ?: return
        val newIndex = (loaded.selectedIndex - 1).coerceAtLeast(0)
        state = loaded.copy(selectedIndex = newIndex, scrollOffset = adjustScroll(newIndex, loaded.scrollOffset))
    }

    private fun adjustScroll(selectedIndex: Int, currentScroll: Int): Int {
        return when {
            selectedIndex < currentScroll -> selectedIndex
            selectedIndex >= currentScroll + VISIBLE_ROWS -> selectedIndex - VISIBLE_ROWS + 1
            else -> currentScroll
        }
    }

    private fun drawPanel(context: TuiContext) {
        val panelRect = TuiRect(left = 2, top = 1, width = 76, height = 22)
        Panel.draw(context = context, rect = panelRect)
        drawHeader(context, panelRect)

        when (val currentState = state) {
            is RiskAnalysisState.Loading -> drawLoading(context, panelRect)
            is RiskAnalysisState.Error -> drawError(context, panelRect, currentState.message)
            is RiskAnalysisState.Loaded -> drawLoaded(context, panelRect, currentState)
        }

        drawFooter(context, panelRect)
    }

    private fun drawHeader(
        context: TuiContext,
        panel: TuiRect,
    ) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Risk Analysis",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        val subtitle = when (val currentState = state) {
            RiskAnalysisState.Loading -> "Scanning Redis keyspace..."
            is RiskAnalysisState.Error -> "Error loading risk analysis"
            is RiskAnalysisState.Loaded -> {
                "View ${currentState.viewMode.label}   sort ${currentState.sortMode.label}   ${currentState.snapshot.analyzedKeyCount} keys   ${currentState.snapshot.warnings.size} warnings"
            }
        }

        context.putText(
            column = panel.left + 3,
            row = panel.top + 2,
            text = fit(subtitle, panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawLoading(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 7,
            text = "Loading risk analysis...",
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

    private fun drawLoaded(
        context: TuiContext,
        panel: TuiRect,
        loaded: RiskAnalysisState.Loaded,
    ) {
        when (loaded.viewMode) {
            RiskAnalysisViewMode.OVERVIEW -> drawOverview(context, panel, loaded.snapshot)
            RiskAnalysisViewMode.BIG_KEYS,
            RiskAnalysisViewMode.NO_TTL -> drawKeyTable(context, panel, loaded)
            RiskAnalysisViewMode.PATTERNS -> drawPatternTable(context, panel, loaded)
        }
    }

    private fun drawOverview(
        context: TuiContext,
        panel: TuiRect,
        snapshot: RiskAnalysisSnapshot,
    ) {
        val rows = listOf(
            "Analyzed keys: ${snapshot.analyzedKeyCount}",
            "No-TTL keys: ${snapshot.noTtlKeyCount}",
            "Big keys: ${snapshot.bigKeyCount}",
            "Large collections: ${snapshot.largeCollectionKeyCount}   hash:${snapshot.largeHashCount} list:${snapshot.largeListCount} set:${snapshot.largeSetCount} zset:${snapshot.largeZsetCount} stream:${snapshot.largeStreamCount}",
            "Top largest: ${snapshot.topLargestKeys.size}   Top No-TTL: ${snapshot.topNoTtlKeys.size}   Risky patterns: ${snapshot.riskyPatterns.size}",
        )

        rows.forEachIndexed { index, row ->
            context.putText(
                column = panel.left + 3,
                row = panel.top + 5 + index,
                text = fit(row, panel.width - 6),
                foregroundColor = context.theme.value,
                backgroundColor = context.theme.panel,
            )
        }

        context.putText(
            column = panel.left + 3,
            row = panel.top + 11,
            text = "Policy warnings",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        if (snapshot.warnings.isEmpty()) {
            context.putText(panel.left + 3, panel.top + 13, "No maxmemory or eviction warnings.", context.theme.hint, context.theme.panel)
            return
        }

        snapshot.warnings.take(5).forEachIndexed { index, warning ->
            context.putText(
                column = panel.left + 3,
                row = panel.top + 13 + index,
                text = fit("${warning.level.name.lowercase()}: ${warning.message}", panel.width - 6),
                foregroundColor = riskColor(context, warning.level),
                backgroundColor = context.theme.panel,
            )
        }
    }

    private fun drawKeyTable(
        context: TuiContext,
        panel: TuiRect,
        loaded: RiskAnalysisState.Loaded,
    ) {
        if (loaded.visibleKeys.isEmpty()) {
            val message = if (loaded.viewMode == RiskAnalysisViewMode.BIG_KEYS) {
                "No keys with known MEMORY USAGE were found."
            } else {
                "No keys without TTL were found."
            }
            context.putText(panel.left + 3, panel.top + 7, message, context.theme.hint, context.theme.panel)
            return
        }

        val headerRow = panel.top + 4
        context.putText(panel.left + COL_RANK, headerRow, "#", context.theme.label, context.theme.panel)
        context.putText(panel.left + COL_KEY, headerRow, "Key", context.theme.label, context.theme.panel)
        context.putText(panel.left + COL_TYPE, headerRow, "Type", context.theme.label, context.theme.panel)
        context.putText(panel.left + COL_MEMORY, headerRow, "Memory", context.theme.label, context.theme.panel)
        context.putText(panel.left + COL_TTL, headerRow, "TTL", context.theme.label, context.theme.panel)
        context.putText(panel.left + COL_SIZE, headerRow, "Size", context.theme.label, context.theme.panel)
        drawSeparator(context, panel, panel.top + 5)

        loaded.visibleKeys.drop(loaded.scrollOffset).take(VISIBLE_ROWS).forEachIndexed { relativeIndex, key ->
            val absoluteIndex = loaded.scrollOffset + relativeIndex
            val row = panel.top + FIRST_ENTRY_ROW + relativeIndex
            val isSelected = absoluteIndex == loaded.selectedIndex
            val bg = if (isSelected) context.theme.border else context.theme.panel
            val fg = if (isSelected) context.theme.panel else keyColor(context, key)

            context.putText(panel.left + COL_RANK, row, (absoluteIndex + 1).toString().padEnd(3), fg, bg)
            context.putText(panel.left + COL_KEY, row, fit(key.name, COL_TYPE - COL_KEY - 1).padEnd(COL_TYPE - COL_KEY), fg, bg)
            context.putText(panel.left + COL_TYPE, row, key.type.name.lowercase().padEnd(COL_MEMORY - COL_TYPE), fg, bg)
            context.putText(panel.left + COL_MEMORY, row, formatMemory(key.memoryUsage).padEnd(COL_TTL - COL_MEMORY), fg, bg)
            context.putText(panel.left + COL_TTL, row, formatTtl(key.ttl).padEnd(COL_SIZE - COL_TTL), fg, bg)
            context.putText(panel.left + COL_SIZE, row, (key.collectionSize?.toString() ?: "-").take(8), fg, bg)
        }

        drawKeyDetail(context, panel, loaded.selectedKey)
    }

    private fun drawPatternTable(
        context: TuiContext,
        panel: TuiRect,
        loaded: RiskAnalysisState.Loaded,
    ) {
        if (loaded.visiblePatterns.isEmpty()) {
            context.putText(panel.left + 3, panel.top + 7, "No risky patterns were found.", context.theme.hint, context.theme.panel)
            return
        }

        val headerRow = panel.top + 4
        context.putText(panel.left + COL_RANK, headerRow, "#", context.theme.label, context.theme.panel)
        context.putText(panel.left + COL_KEY, headerRow, "Pattern", context.theme.label, context.theme.panel)
        context.putText(panel.left + 35, headerRow, "Keys", context.theme.label, context.theme.panel)
        context.putText(panel.left + 44, headerRow, "NoTTL", context.theme.label, context.theme.panel)
        context.putText(panel.left + 53, headerRow, "Big", context.theme.label, context.theme.panel)
        context.putText(panel.left + 61, headerRow, "Large", context.theme.label, context.theme.panel)
        drawSeparator(context, panel, panel.top + 5)

        loaded.visiblePatterns.drop(loaded.scrollOffset).take(VISIBLE_ROWS).forEachIndexed { relativeIndex, pattern ->
            val absoluteIndex = loaded.scrollOffset + relativeIndex
            val row = panel.top + FIRST_ENTRY_ROW + relativeIndex
            val isSelected = absoluteIndex == loaded.selectedIndex
            val bg = if (isSelected) context.theme.border else context.theme.panel
            val fg = if (isSelected) context.theme.panel else riskColor(context, pattern.riskLevel)

            context.putText(panel.left + COL_RANK, row, (absoluteIndex + 1).toString().padEnd(3), fg, bg)
            context.putText(panel.left + COL_KEY, row, fit(pattern.patternName, 27).padEnd(28), fg, bg)
            context.putText(panel.left + 35, row, pattern.keyCount.toString().padStart(6), fg, bg)
            context.putText(panel.left + 44, row, pattern.noTtlKeyCount.toString().padStart(6), fg, bg)
            context.putText(panel.left + 53, row, pattern.bigKeyCount.toString().padStart(4), fg, bg)
            context.putText(panel.left + 61, row, pattern.largeCollectionKeyCount.toString().padStart(5), fg, bg)
        }

        drawPatternDetail(context, panel, loaded.selectedPattern)
    }

    private fun drawKeyDetail(
        context: TuiContext,
        panel: TuiRect,
        key: RiskKeyFinding?,
    ) {
        drawSeparator(context, panel, panel.top + DETAIL_SEPARATOR_ROW)
        if (key == null) return

        context.putText(
            column = panel.left + 3,
            row = panel.top + DETAIL_FIRST_ROW,
            text = fit("Selected: ${key.name}", panel.width - 6),
            foregroundColor = context.theme.value,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + DETAIL_SECOND_ROW,
            text = fit(
                "type:${key.type.name.lowercase()} memory:${formatMemory(key.memoryUsage)} ttl:${formatTtl(key.ttl)} size:${key.collectionSize ?: "-"}",
                panel.width - 6,
            ),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + DETAIL_THIRD_ROW,
            text = fit("risks: ${formatReasons(key)}", panel.width - 6),
            foregroundColor = keyColor(context, key),
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawPatternDetail(
        context: TuiContext,
        panel: TuiRect,
        pattern: RiskPatternFinding?,
    ) {
        drawSeparator(context, panel, panel.top + DETAIL_SEPARATOR_ROW)
        if (pattern == null) return

        context.putText(
            column = panel.left + 3,
            row = panel.top + DETAIL_FIRST_ROW,
            text = fit("Selected: ${pattern.patternName}   level:${pattern.riskLevel.name.lowercase()}", panel.width - 6),
            foregroundColor = riskColor(context, pattern.riskLevel),
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + DETAIL_SECOND_ROW,
            text = fit(
                "keys:${pattern.keyCount} no-ttl:${pattern.noTtlKeyCount} big:${pattern.bigKeyCount} large:${pattern.largeCollectionKeyCount}",
                panel.width - 6,
            ),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + DETAIL_THIRD_ROW,
            text = fit("estimated memory: ${byteSizeFormatter.format(pattern.estimatedMemoryBytes)}", panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawFooter(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + panel.height - 2,
            text = fit("j/k:move  v/tab:view  s:sort  r:refresh  b/esc:back  q:exit", panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawSeparator(context: TuiContext, panel: TuiRect, row: Int) {
        context.putText(
            column = panel.left + 2,
            row = row,
            text = "-".repeat(panel.width - 4),
            foregroundColor = context.theme.border,
            backgroundColor = context.theme.panel,
        )
    }

    private fun keyColor(context: TuiContext, key: RiskKeyFinding): TextColor {
        return when {
            key.riskReasons.size >= 2 -> context.theme.danger
            key.riskReasons.isNotEmpty() -> context.theme.warning
            else -> context.theme.value
        }
    }

    private fun riskColor(context: TuiContext, level: RiskLevel): TextColor {
        return when (level) {
            RiskLevel.INFO -> context.theme.hint
            RiskLevel.WATCH -> context.theme.warning
            RiskLevel.RISKY -> context.theme.warning
            RiskLevel.CRITICAL -> context.theme.danger
        }
    }

    private fun formatMemory(memoryUsage: RedisKeyMemoryUsage): String {
        return when (memoryUsage) {
            RedisKeyMemoryUsage.Unknown -> "unknown"
            is RedisKeyMemoryUsage.Known -> byteSizeFormatter.format(memoryUsage.bytes)
        }
    }

    private fun formatTtl(ttl: RedisKeyTtlStatus): String {
        return when (ttl) {
            RedisKeyTtlStatus.KeyDoesNotExist -> "gone"
            RedisKeyTtlStatus.NoExpiration -> "none"
            is RedisKeyTtlStatus.Expiring -> "${ttl.seconds}s"
            is RedisKeyTtlStatus.Unknown -> "unknown"
        }
    }

    private fun formatReasons(key: RiskKeyFinding): String {
        return key.riskReasons
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = ",") { it.name.lowercase() }
            ?: "none"
    }

    private fun fit(text: String, maxWidth: Int): String {
        return text.take(maxWidth.coerceAtLeast(0))
    }

    private fun isExitKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.EOF || isCharacter(keyStroke, 'q')
    }

    private fun isBackKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.Escape || isCharacter(keyStroke, 'b')
    }

    private fun isViewKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.Tab || isCharacter(keyStroke, 'v')
    }

    private fun isCharacter(
        keyStroke: KeyStroke,
        expected: Char,
    ): Boolean {
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
        const val COL_KEY = 7
        const val COL_TYPE = 35
        const val COL_MEMORY = 44
        const val COL_TTL = 56
        const val COL_SIZE = 64
    }
}
