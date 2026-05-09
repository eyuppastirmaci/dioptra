package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.latency.LoadLatencyStatsUseCase
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.latency.LatencyStatsState
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.latency.LatencyStatsSortMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Displays `INFO latencystats` percentile data in a scrollable, sortable table.
 *
 * Requires Redis 7.0+ with latency-tracking enabled (on by default since 7.0).
 * If the section is empty an informative message is shown instead of an error.
 *
 * Columns: Rank | Command | p50 µs | p99 µs | p99.9 µs
 *
 * Key bindings:
 *   ↑/k   = scroll up
 *   ↓/j   = scroll down
 *   s     = cycle sort mode (p99 → p50 → p99.9 → command → p99)
 *   r     = refresh
 *   b/ESC = back
 *   q     = exit
 */
class LatencyStatsScreen(
    private val loadLatencyStatsUseCase: LoadLatencyStatsUseCase,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private val logger = LoggerFactory.getLogger(LatencyStatsScreen::class.java)
    private val screenScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            DioptraCoroutineExceptionHandler.create(
                logger = logger,
                contextName = "LatencyStatsScreen",
                onError = { exception ->
                    state = LatencyStatsState.Error(UserFacingErrorMessage.from(exception))
                },
            ),
    )

    private var loadJob: Job? = null

    @Volatile
    private var state: LatencyStatsState = LatencyStatsState.Loading

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
                state = LatencyStatsState.Loading
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
            val stats = loadLatencyStatsUseCase.load()
            state = LatencyStatsState.Loaded(stats = stats)
        }
    }

    // ─── Sort ────────────────────────────────────────────────────────────────

    private fun cycleSortMode() {
        val loaded = state as? LatencyStatsState.Loaded ?: return
        val nextMode = when (loaded.sortMode) {
            LatencyStatsSortMode.P99     -> LatencyStatsSortMode.P50
            LatencyStatsSortMode.P50     -> LatencyStatsSortMode.P999
            LatencyStatsSortMode.P999    -> LatencyStatsSortMode.COMMAND
            LatencyStatsSortMode.COMMAND -> LatencyStatsSortMode.P99
        }
        state = loaded.copy(sortMode = nextMode, selectedIndex = 0, scrollOffset = 0)
    }

    // ─── Scroll ───────────────────────────────────────────────────────────────

    private fun scrollDown() {
        val loaded = state as? LatencyStatsState.Loaded ?: return
        val maxIndex = (loaded.stats.size - 1).coerceAtLeast(0)
        val newIndex = (loaded.selectedIndex + 1).coerceAtMost(maxIndex)
        val newScroll = adjustScroll(newIndex, loaded.scrollOffset)
        state = loaded.copy(selectedIndex = newIndex, scrollOffset = newScroll)
    }

    private fun scrollUp() {
        val loaded = state as? LatencyStatsState.Loaded ?: return
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
            is LatencyStatsState.Loading -> drawLoading(context, panelRect)
            is LatencyStatsState.Error   -> drawError(context, panelRect, s.message)
            is LatencyStatsState.Loaded  -> drawStats(context, panelRect, s)
        }

        drawFooter(context, panelRect)
    }

    private fun drawHeader(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Latency Stats",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        val subtitle = when (val s = state) {
            is LatencyStatsState.Loaded -> {
                val count = s.stats.size
                if (count == 0) {
                    "No data — requires Redis 7.0+ with latency-tracking enabled"
                } else {
                    "Sorted by ${s.sortMode.label}   $count commands tracked"
                }
            }
            is LatencyStatsState.Loading -> "Loading..."
            is LatencyStatsState.Error   -> "Error loading stats"
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
            row = panel.top + 7,
            text = "Loading latency stats...",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawError(context: TuiContext, panel: TuiRect, message: String) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 7,
            text = "Error: $message",
            foregroundColor = context.theme.danger,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawStats(context: TuiContext, panel: TuiRect, loaded: LatencyStatsState.Loaded) {
        if (loaded.stats.isEmpty()) {
            context.putText(
                column = panel.left + 3,
                row = panel.top + 7,
                text = "No latency data available.",
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            context.putText(
                column = panel.left + 3,
                row = panel.top + 9,
                text = "Make sure latency-tracking is enabled in redis.conf:",
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            context.putText(
                column = panel.left + 5,
                row = panel.top + 10,
                text = "latency-tracking yes",
                foregroundColor = context.theme.value,
                backgroundColor = context.theme.panel,
                bold = true,
            )
            context.putText(
                column = panel.left + 3,
                row = panel.top + 11,
                text = "Requires Redis 7.0 or later.",
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            return
        }

        // Column headers
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
            text = "Command".padEnd(COL_P50 - COL_COMMAND),
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + COL_P50,
            row = headerRow,
            text = "p50 µs".padEnd(COL_P99 - COL_P50),
            foregroundColor = if (loaded.sortMode == LatencyStatsSortMode.P50) context.theme.title else context.theme.label,
            backgroundColor = context.theme.panel,
            bold = loaded.sortMode == LatencyStatsSortMode.P50,
        )
        context.putText(
            column = panel.left + COL_P99,
            row = headerRow,
            text = "p99 µs".padEnd(COL_P999 - COL_P99),
            foregroundColor = if (loaded.sortMode == LatencyStatsSortMode.P99) context.theme.title else context.theme.label,
            backgroundColor = context.theme.panel,
            bold = loaded.sortMode == LatencyStatsSortMode.P99,
        )
        context.putText(
            column = panel.left + COL_P999,
            row = headerRow,
            text = "p99.9 µs",
            foregroundColor = if (loaded.sortMode == LatencyStatsSortMode.P999) context.theme.title else context.theme.label,
            backgroundColor = context.theme.panel,
            bold = loaded.sortMode == LatencyStatsSortMode.P999,
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

            // Command
            context.putText(
                column = panel.left + COL_COMMAND,
                row = row,
                text = stat.command.padEnd(COL_P50 - COL_COMMAND),
                foregroundColor = fgTitle,
                backgroundColor = bg,
                bold = true,
            )

            // p50
            context.putText(
                column = panel.left + COL_P50,
                row = row,
                text = formatUsec(stat.p50Usec).padEnd(COL_P99 - COL_P50),
                foregroundColor = fgValue,
                backgroundColor = bg,
            )

            // p99 — color-coded by severity
            val p99Color = when {
                isSelected -> context.theme.panel
                stat.p99Usec >= 100_000.0 -> context.theme.danger    // ≥ 100ms
                stat.p99Usec >= 10_000.0  -> context.theme.warning   // ≥ 10ms
                else -> fgValue
            }
            context.putText(
                column = panel.left + COL_P99,
                row = row,
                text = formatUsec(stat.p99Usec).padEnd(COL_P999 - COL_P99),
                foregroundColor = p99Color,
                backgroundColor = bg,
            )

            // p99.9 — color-coded by severity
            val p999Color = when {
                isSelected -> context.theme.panel
                stat.p999Usec >= 100_000.0 -> context.theme.danger
                stat.p999Usec >= 10_000.0  -> context.theme.warning
                else -> fgValue
            }
            context.putText(
                column = panel.left + COL_P999,
                row = row,
                text = formatUsec(stat.p999Usec),
                foregroundColor = p999Color,
                backgroundColor = bg,
            )
        }

        // Sort indicator
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

    private fun drawScrollIndicator(context: TuiContext, panel: TuiRect, loaded: LatencyStatsState.Loaded) {
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

    /**
     * Formats a microsecond value to a readable string.
     * Values ≥ 1ms are shown in ms with one decimal; ≥ 1s in seconds.
     */
    private fun formatUsec(usec: Double): String {
        return when {
            usec >= 1_000_000.0 -> "${"%.2f".format(usec / 1_000_000.0)}s"
            usec >= 1_000.0     -> "${"%.1f".format(usec / 1_000.0)}ms"
            else                -> "${"%.1f".format(usec)}µs"
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
        // Panel: width=76, content from left+3 to left+73
        const val COL_RANK    = 3   // 3 chars
        const val COL_COMMAND = 7   // 15 chars wide → ends at 22
        const val COL_P50     = 22  // 13 chars wide → ends at 35
        const val COL_P99     = 35  // 13 chars wide → ends at 48
        const val COL_P999    = 48  // rest

        const val VISIBLE_ROWS    = 13
        const val FIRST_ENTRY_ROW = 6
    }
}
