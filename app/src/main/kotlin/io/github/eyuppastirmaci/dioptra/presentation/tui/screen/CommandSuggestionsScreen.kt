package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.risk.LoadRiskAnalysisUseCase
import io.github.eyuppastirmaci.dioptra.application.suggestion.CommandSuggestionEngine
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.domain.suggestion.CommandSuggestion
import io.github.eyuppastirmaci.dioptra.domain.suggestion.SuggestionCategory
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.suggestion.CommandSuggestionsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class CommandSuggestionsScreen(
    private val dashboardSnapshot: RedisDashboardSnapshot,
    private val loadRiskAnalysisUseCase: LoadRiskAnalysisUseCase,
    private val engine: CommandSuggestionEngine,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private val logger = LoggerFactory.getLogger(CommandSuggestionsScreen::class.java)
    private val screenScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            DioptraCoroutineExceptionHandler.create(
                logger = logger,
                contextName = "CommandSuggestionsScreen",
                onError = { exception ->
                    val dashboardSuggestions = engine.generate(dashboardSnapshot, riskAnalysis = null)
                    state = state.copy(
                        suggestions = dashboardSuggestions,
                        riskStatus = CommandSuggestionsState.RiskStatus.Error(
                            UserFacingErrorMessage.from(exception)
                        ),
                    )
                },
            ),
    )

    private var riskJob: Job? = null

    @Volatile
    private var state: CommandSuggestionsState = CommandSuggestionsState(
        suggestions = engine.generate(dashboardSnapshot, riskAnalysis = null),
        riskStatus = CommandSuggestionsState.RiskStatus.Loading,
    )

    init {
        loadRisk()
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
                reloadRisk()
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
        riskJob?.cancel()
        screenScope.cancel()
    }

    private fun loadRisk() {
        riskJob?.cancel()
        riskJob = screenScope.launch(Dispatchers.IO) {
            val riskAnalysis = loadRiskAnalysisUseCase.load()
            val allSuggestions = engine.generate(dashboardSnapshot, riskAnalysis)
            state = state.copy(
                suggestions = allSuggestions,
                riskStatus = CommandSuggestionsState.RiskStatus.Loaded,
                selectedIndex = 0,
                scrollOffset = 0,
            )
        }
    }

    private fun reloadRisk() {
        val dashboardSuggestions = engine.generate(dashboardSnapshot, riskAnalysis = null)
        state = state.copy(
            suggestions = dashboardSuggestions,
            riskStatus = CommandSuggestionsState.RiskStatus.Loading,
            selectedIndex = 0,
            scrollOffset = 0,
        )
        loadRisk()
    }

    private fun scrollDown() {
        val maxIndex = (state.suggestions.size - 1).coerceAtLeast(0)
        val newIndex = (state.selectedIndex + 1).coerceAtMost(maxIndex)
        state = state.copy(
            selectedIndex = newIndex,
            scrollOffset = adjustScroll(newIndex, state.scrollOffset),
        )
    }

    private fun scrollUp() {
        val newIndex = (state.selectedIndex - 1).coerceAtLeast(0)
        state = state.copy(
            selectedIndex = newIndex,
            scrollOffset = adjustScroll(newIndex, state.scrollOffset),
        )
    }

    private fun adjustScroll(selectedIndex: Int, currentScroll: Int): Int {
        return when {
            selectedIndex < currentScroll -> selectedIndex
            selectedIndex >= currentScroll + VISIBLE_ROWS -> selectedIndex - VISIBLE_ROWS + 1
            else -> currentScroll
        }
    }

    // ─── Rendering ────────────────────────────────────────────────────────────

    private fun drawPanel(context: TuiContext) {
        val panel = TuiRect(left = 2, top = 1, width = 76, height = 22)
        Panel.draw(context = context, rect = panel)
        drawHeader(context, panel)
        drawColumnHeaders(context, panel)
        drawList(context, panel)
        drawDetail(context, panel)
        drawFooter(context, panel)
    }

    private fun drawHeader(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Command Suggestions",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        val currentState = state
        val total = currentState.suggestions.size
        val dashCount = currentState.dashboardCount
        val riskCount = currentState.riskCount

        val countText = if (riskCount > 0) {
            "$total suggestions  ($dashCount from dashboard · $riskCount from risk analysis)"
        } else {
            "$dashCount suggestions from dashboard"
        }

        context.putText(
            column = panel.left + 3,
            row = panel.top + 2,
            text = fit(countText, panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        val riskStatusText = when (val rs = currentState.riskStatus) {
            CommandSuggestionsState.RiskStatus.Loading -> "Scanning keyspace for risk-based suggestions..."
            CommandSuggestionsState.RiskStatus.Loaded -> "Risk analysis loaded. Press r to refresh."
            is CommandSuggestionsState.RiskStatus.Error -> "Risk analysis unavailable: ${rs.message}"
        }
        val riskStatusColor = when (currentState.riskStatus) {
            CommandSuggestionsState.RiskStatus.Loading -> context.theme.hint
            CommandSuggestionsState.RiskStatus.Loaded -> context.theme.success
            is CommandSuggestionsState.RiskStatus.Error -> context.theme.warning
        }

        context.putText(
            column = panel.left + 3,
            row = panel.top + 3,
            text = fit(riskStatusText, panel.width - 6),
            foregroundColor = riskStatusColor,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawColumnHeaders(context: TuiContext, panel: TuiRect) {
        val headerRow = panel.top + 4
        val bg = context.theme.panel

        context.putText(
            column = panel.left + COL_BADGE,
            row = headerRow,
            text = "Category",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )
        context.putText(
            column = panel.left + COL_TITLE,
            row = headerRow,
            text = "Suggestion",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )
        context.putText(
            column = panel.left + COL_CMD,
            row = headerRow,
            text = "Command(s)",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )

        context.putText(
            column = panel.left + 2,
            row = panel.top + 5,
            text = "─".repeat(panel.width - 4),
            foregroundColor = context.theme.border,
            backgroundColor = bg,
        )
    }

    private fun drawList(context: TuiContext, panel: TuiRect) {
        val currentState = state
        val suggestions = currentState.suggestions

        if (suggestions.isEmpty()) {
            context.putText(
                column = panel.left + 3,
                row = panel.top + FIRST_ROW + 2,
                text = "No suggestions available.",
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            return
        }

        val visible = suggestions.drop(currentState.scrollOffset).take(VISIBLE_ROWS)
        visible.forEachIndexed { relativeIndex, suggestion ->
            val absoluteIndex = currentState.scrollOffset + relativeIndex
            val row = panel.top + FIRST_ROW + relativeIndex
            val isSelected = absoluteIndex == currentState.selectedIndex
            drawSuggestionRow(context, panel, row, suggestion, isSelected)
        }

        if (suggestions.size > VISIBLE_ROWS) {
            val indicatorRow = panel.top + FIRST_ROW + VISIBLE_ROWS
            val text = "↑↓/j/k scroll  (${currentState.selectedIndex + 1}/${suggestions.size})"
            context.putText(
                column = panel.left + 3,
                row = indicatorRow,
                text = text,
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
        }
    }

    private fun drawSuggestionRow(
        context: TuiContext,
        panel: TuiRect,
        row: Int,
        suggestion: CommandSuggestion,
        isSelected: Boolean,
    ) {
        val bg = if (isSelected) context.theme.border else context.theme.panel
        val fg = if (isSelected) context.theme.panel else context.theme.value
        val fgBadge = if (isSelected) context.theme.panel else badgeColor(suggestion.category, context)

        val rowWidth = panel.width - 4
        context.putText(
            column = panel.left + 2,
            row = row,
            text = " ".repeat(rowWidth),
            foregroundColor = fg,
            backgroundColor = bg,
        )

        context.putText(
            column = panel.left + COL_BADGE,
            row = row,
            text = "[${suggestion.category.badge}]",
            foregroundColor = fgBadge,
            backgroundColor = bg,
        )

        context.putText(
            column = panel.left + COL_TITLE,
            row = row,
            text = fit(suggestion.title, COL_CMD - COL_TITLE - 1).padEnd(COL_CMD - COL_TITLE),
            foregroundColor = fg,
            backgroundColor = bg,
            bold = isSelected,
        )

        val firstCommand = suggestion.commands.firstOrNull() ?: ""
        val cmdWidth = panel.width - COL_CMD - 5
        context.putText(
            column = panel.left + COL_CMD,
            row = row,
            text = fit(firstCommand, cmdWidth),
            foregroundColor = if (isSelected) context.theme.panel else context.theme.label,
            backgroundColor = bg,
        )
    }

    private fun drawDetail(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 2,
            row = panel.top + DETAIL_SEPARATOR_ROW,
            text = "─".repeat(panel.width - 4),
            foregroundColor = context.theme.border,
            backgroundColor = context.theme.panel,
        )

        val selected = state.selectedSuggestion ?: return

        context.putText(
            column = panel.left + 3,
            row = panel.top + DETAIL_TITLE_ROW,
            text = fit(selected.title, panel.width - 6),
            foregroundColor = context.theme.value,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        val reasonText = selected.reason?.let { "Reason: $it" } ?: ""
        if (reasonText.isNotEmpty()) {
            context.putText(
                column = panel.left + 3,
                row = panel.top + DETAIL_REASON_ROW,
                text = fit(reasonText, panel.width - 6),
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
        }

        val commands = selected.commands
        if (commands.isNotEmpty()) {
            val line1 = commands.take(2).joinToString("   ")
            context.putText(
                column = panel.left + 3,
                row = panel.top + DETAIL_CMD_ROW_1,
                text = fit(line1, panel.width - 6),
                foregroundColor = context.theme.value,
                backgroundColor = context.theme.panel,
            )
        }

        if (commands.size > 2) {
            val remaining = commands.drop(2)
            val line2 = remaining.take(2).joinToString("   ")
            val suffix = if (remaining.size > 2) "  …+${remaining.size - 2}" else ""
            context.putText(
                column = panel.left + 3,
                row = panel.top + DETAIL_CMD_ROW_2,
                text = fit(line2 + suffix, panel.width - 6),
                foregroundColor = context.theme.label,
                backgroundColor = context.theme.panel,
            )
        }
    }

    private fun drawFooter(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + panel.height - 2,
            text = fit("j/k:move  r:refresh risk  b/esc:back  q:exit", panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun badgeColor(category: SuggestionCategory, context: TuiContext) = when (category) {
        SuggestionCategory.MEMORY -> context.theme.warning
        SuggestionCategory.CLIENTS -> context.theme.hint
        SuggestionCategory.PERSISTENCE -> context.theme.warning
        SuggestionCategory.REPLICATION -> context.theme.hint
        SuggestionCategory.PERFORMANCE -> context.theme.label
        SuggestionCategory.KEYSPACE -> context.theme.label
        SuggestionCategory.RISK -> context.theme.danger
    }

    private fun fit(text: String, maxWidth: Int): String =
        text.take(maxWidth.coerceAtLeast(0))

    private fun isExitKey(keyStroke: KeyStroke): Boolean =
        keyStroke.keyType == KeyType.EOF || isCharacter(keyStroke, 'q')

    private fun isBackKey(keyStroke: KeyStroke): Boolean =
        keyStroke.keyType == KeyType.Escape || isCharacter(keyStroke, 'b')

    private fun isCharacter(keyStroke: KeyStroke, expected: Char): Boolean =
        keyStroke.keyType == KeyType.Character && keyStroke.character?.lowercaseChar() == expected

    private companion object {
        const val VISIBLE_ROWS = 8
        const val FIRST_ROW = 6
        const val DETAIL_SEPARATOR_ROW = 15
        const val DETAIL_TITLE_ROW = 16
        const val DETAIL_REASON_ROW = 17
        const val DETAIL_CMD_ROW_1 = 18
        const val DETAIL_CMD_ROW_2 = 19

        const val COL_BADGE = 3
        const val COL_TITLE = 12
        const val COL_CMD = 43
    }
}
