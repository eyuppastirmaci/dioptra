package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.commandstats.LoadCommandStatsUseCase
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.commandstats.CommandStatsState
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.commandstats.CommandStatsSortMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Displays `INFO commandstats` data in a scrollable, sortable table.
 *
 * Columns: Rank | Command | Calls | Avg µs | Total µs | Rejected | Failed
 *
 * Key bindings:
 *   ↑/k  = scroll up
 *   ↓/j  = scroll down
 *   s    = cycle sort mode (total → calls → avg)
 *   r    = refresh
 *   b/ESC = back
 *   q    = exit
 */
class CommandStatsScreen(
    private val loadCommandStatsUseCase: LoadCommandStatsUseCase,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private val logger = LoggerFactory.getLogger(CommandStatsScreen::class.java)
    private val screenScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            DioptraCoroutineExceptionHandler.create(
                logger = logger,
                contextName = "CommandStatsScreen",
                onError = { exception ->
                    state = CommandStatsState.Error(UserFacingErrorMessage.from(exception))
                },
            ),
    )

    private var loadJob: Job? = null

    @Volatile
    private var state: CommandStatsState = CommandStatsState.Loading

    init {
        load()
    }

    // ─── TuiScreen ───────────────────────────────────────────────────────────

    override fun render(context: TuiContext) {
        context.clearBackground()
        drawPanel(context)
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isExitKey(keyStroke) -> TuiScreenResult.Exit
            isBackKey(keyStroke) -> TuiScreenResult.Navigate(back())
            isCharacter(keyStroke, 'r') -> {
                state = CommandStatsState.Loading
                load()
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

    // ─── Loading ─────────────────────────────────────────────────────────────

    private fun load() {
        loadJob?.cancel()
        loadJob = screenScope.launch(Dispatchers.IO) {
            val stats = loadCommandStatsUseCase.load()
            state = CommandStatsState.Loaded(stats = stats)
        }
    }

    // ─── Sort ────────────────────────────────────────────────────────────────

    private fun cycleSortMode() {
        val loaded = state as? CommandStatsState.Loaded ?: return
        val nextMode = when (loaded.sortMode) {
            CommandStatsSortMode.TOTAL_USEC -> CommandStatsSortMode.CALLS
            CommandStatsSortMode.CALLS -> CommandStatsSortMode.AVG_USEC
            CommandStatsSortMode.AVG_USEC -> CommandStatsSortMode.TOTAL_USEC
        }
        state = loaded.copy(sortMode = nextMode, selectedIndex = 0, scrollOffset = 0)
    }

    // ─── Scroll ───────────────────────────────────────────────────────────────

    private fun scrollDown() {
        val loaded = state as? CommandStatsState.Loaded ?: return
        val maxIndex = (loaded.stats.size - 1).coerceAtLeast(0)
        val newIndex = (loaded.selectedIndex + 1).coerceAtMost(maxIndex)
        val newScroll = adjustScroll(newIndex, loaded.scrollOffset)
        state = loaded.copy(selectedIndex = newIndex, scrollOffset = newScroll)
    }

    private fun scrollUp() {
        val loaded = state as? CommandStatsState.Loaded ?: return
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

    // ─── Rendering ────────────────────────────────────────────────────────────

    private fun drawPanel(context: TuiContext) {
        val panelRect = TuiRect(left = 2, top = 1, width = 76, height = 22)
        Panel.draw(context = context, rect = panelRect)

        drawHeader(context, panelRect)

        when (val s = state) {
            is CommandStatsState.Loading -> drawLoading(context, panelRect)
            is CommandStatsState.Error -> drawError(context, panelRect, s.message)
            is CommandStatsState.Loaded -> drawStats(context, panelRect, s)
        }

        drawFooter(context, panelRect)
    }

    private fun drawHeader(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Command Stats",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        val subtitle = when (val s = state) {
            is CommandStatsState.Loaded -> {
                val total = s.stats.size
                "Sorted by ${s.sortMode.label}   $total commands tracked"
            }
            is CommandStatsState.Loading -> "Loading..."
            is CommandStatsState.Error -> "Error loading stats"
        }

        context.putText(
            column = panel.left + 3,
            row = panel.top + 2,
            text = subtitle,
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawLoading(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 6,
            text = "Loading command stats...",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawError(context: TuiContext, panel: TuiRect, message: String) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 6,
            text = "Error: $message",
            foregroundColor = context.theme.danger,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawStats(context: TuiContext, panel: TuiRect, loaded: CommandStatsState.Loaded) {
        // Column header row
        val headerRow = panel.top + 4
        context.putText(
            column = panel.left + COL_RANK,
            row = headerRow,
            text = "#".padEnd(COL_COMMAND - COL_RANK),
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + COL_COMMAND,
            row = headerRow,
            text = "Command".padEnd(COL_CALLS - COL_COMMAND),
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + COL_CALLS,
            row = headerRow,
            text = "Calls".padEnd(COL_AVG - COL_CALLS),
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + COL_AVG,
            row = headerRow,
            text = "Avg µs".padEnd(COL_TOTAL - COL_AVG),
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + COL_TOTAL,
            row = headerRow,
            text = "Total µs".padEnd(COL_REJECTED - COL_TOTAL),
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + COL_REJECTED,
            row = headerRow,
            text = "Rejected".padEnd(COL_FAILED - COL_REJECTED),
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + COL_FAILED,
            row = headerRow,
            text = "Failed",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )

        // Separator
        context.putText(
            column = panel.left + 2,
            row = panel.top + 5,
            text = "─".repeat(panel.width - 4),
            foregroundColor = context.theme.border,
            backgroundColor = context.theme.panel,
        )

        // Rows
        val sorted = loaded.sorted
        val visibleStats = sorted.drop(loaded.scrollOffset).take(VISIBLE_ROWS)
        visibleStats.forEachIndexed { relativeIndex, stat ->
            val absoluteIndex = loaded.scrollOffset + relativeIndex
            val row = panel.top + FIRST_ENTRY_ROW + relativeIndex
            val isSelected = absoluteIndex == loaded.selectedIndex

            val bg = if (isSelected) context.theme.border else context.theme.panel
            val fgValue = if (isSelected) context.theme.panel else context.theme.value
            val fgTitle = if (isSelected) context.theme.panel else context.theme.title

            // Fill row
            context.putText(
                column = panel.left + 3,
                row = row,
                text = " ".repeat(panel.width - 6),
                foregroundColor = fgValue,
                backgroundColor = bg,
            )

            // Rank
            context.putText(
                column = panel.left + COL_RANK,
                row = row,
                text = (absoluteIndex + 1).toString().padStart(3),
                foregroundColor = fgValue,
                backgroundColor = bg,
            )

            // Command name
            context.putText(
                column = panel.left + COL_COMMAND,
                row = row,
                text = stat.command.padEnd(COL_CALLS - COL_COMMAND),
                foregroundColor = fgTitle,
                backgroundColor = bg,
                bold = true,
            )

            // Calls
            context.putText(
                column = panel.left + COL_CALLS,
                row = row,
                text = formatLong(stat.calls).padEnd(COL_AVG - COL_CALLS),
                foregroundColor = fgValue,
                backgroundColor = bg,
            )

            // Avg µs
            context.putText(
                column = panel.left + COL_AVG,
                row = row,
                text = "%.1f".format(stat.usecPerCall).padEnd(COL_TOTAL - COL_AVG),
                foregroundColor = fgValue,
                backgroundColor = bg,
            )

            // Total µs
            context.putText(
                column = panel.left + COL_TOTAL,
                row = row,
                text = formatLong(stat.totalUsec).padEnd(COL_REJECTED - COL_TOTAL),
                foregroundColor = if (isSelected) context.theme.panel else context.theme.hint,
                backgroundColor = bg,
            )

            // Rejected
            val rejectedColor = when {
                isSelected -> context.theme.panel
                stat.rejectedCalls > 0L -> context.theme.warning
                else -> fgValue
            }
            context.putText(
                column = panel.left + COL_REJECTED,
                row = row,
                text = formatLong(stat.rejectedCalls).padEnd(COL_FAILED - COL_REJECTED),
                foregroundColor = rejectedColor,
                backgroundColor = bg,
            )

            // Failed
            val failedColor = when {
                isSelected -> context.theme.panel
                stat.failedCalls > 0L -> context.theme.danger
                else -> fgValue
            }
            context.putText(
                column = panel.left + COL_FAILED,
                row = row,
                text = formatLong(stat.failedCalls),
                foregroundColor = failedColor,
                backgroundColor = bg,
            )
        }

        // Sort indicator and scroll info
        val sortLabel = "sort: ${loaded.sortMode.label}"
        context.putText(
            column = panel.left + panel.width - sortLabel.length - 3,
            row = panel.top + 2,
            text = sortLabel,
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        drawScrollIndicator(context, panel, loaded)
    }

    private fun drawScrollIndicator(context: TuiContext, panel: TuiRect, loaded: CommandStatsState.Loaded) {
        val size = loaded.stats.size
        if (size <= VISIBLE_ROWS) return
        val end = (loaded.scrollOffset + VISIBLE_ROWS).coerceAtMost(size)
        val indicator = "${loaded.scrollOffset + 1}-$end of $size"
        context.putText(
            column = panel.left + panel.width - indicator.length - 3,
            row = panel.top + panel.height - 3,
            text = indicator,
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawFooter(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + panel.height - 2,
            text = fitToPanelWidth(panel, "s:sort  r:refresh  b/esc:back  q:exit"),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun fitToPanelWidth(panel: TuiRect, text: String): String {
        val maxWidth = (panel.width - 6).coerceAtLeast(0)
        return text.take(maxWidth)
    }

    private fun formatLong(value: Long): String {
        return when {
            value >= 1_000_000_000L -> "${"%.1f".format(value / 1_000_000_000.0)}B"
            value >= 1_000_000L -> "${"%.1f".format(value / 1_000_000.0)}M"
            value >= 1_000L -> "${"%.1f".format(value / 1_000.0)}K"
            else -> value.toString()
        }
    }

    private fun isExitKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.EOF || isCharacter(keyStroke, 'q')
    }

    private fun isBackKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.Escape || isCharacter(keyStroke, 'b')
    }

    private fun isCharacter(keyStroke: KeyStroke, expected: Char): Boolean {
        return keyStroke.keyType == KeyType.Character &&
            keyStroke.character?.lowercaseChar() == expected
    }

    private companion object {
        // Panel: width=76, usable content from left+3 to left+73
        const val COL_RANK    = 3   // 3 chars wide
        const val COL_COMMAND = 7   // 13 chars wide → ends at 20
        const val COL_CALLS   = 20  // 9 chars wide  → ends at 29
        const val COL_AVG     = 29  // 10 chars wide → ends at 39
        const val COL_TOTAL   = 39  // 11 chars wide → ends at 50
        const val COL_REJECTED = 50 // 10 chars wide → ends at 60
        const val COL_FAILED  = 60  // rest

        const val VISIBLE_ROWS = 13
        const val FIRST_ENTRY_ROW = 6
    }
}
