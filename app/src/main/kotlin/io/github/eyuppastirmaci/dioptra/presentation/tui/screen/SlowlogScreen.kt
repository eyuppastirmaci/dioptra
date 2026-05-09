package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.slowlog.LoadSlowlogUseCase
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.domain.slowlog.RedisSlowlogEntry
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.SlowlogGrouper
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.SlowlogRiskClassifier
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.SlowlogRiskLevel
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.SlowlogSuspiciousCommandDetector
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.slowlog.SlowlogState
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.slowlog.SlowlogViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SlowlogScreen(
    private val loadSlowlogUseCase: LoadSlowlogUseCase,
    private val back: () -> TuiScreen,
    private val riskClassifier: SlowlogRiskClassifier = SlowlogRiskClassifier(),
) : TuiScreen {

    private val grouper = SlowlogGrouper(riskClassifier)
    private val suspiciousDetector = SlowlogSuspiciousCommandDetector()

    private val logger = LoggerFactory.getLogger(SlowlogScreen::class.java)
    private val screenScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            DioptraCoroutineExceptionHandler.create(
                logger = logger,
                contextName = "SlowlogScreen",
                onError = { exception ->
                    state = SlowlogState.Error(UserFacingErrorMessage.from(exception))
                },
            ),
    )

    private var loadJob: Job? = null

    @Volatile
    private var state: SlowlogState = SlowlogState.Loading

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
                state = SlowlogState.Loading
                load()
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, 'g') -> {
                toggleViewMode()
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, 'w') -> {
                toggleWarningsMode()
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
            val snapshot = loadSlowlogUseCase.load()
            val groups = grouper.group(snapshot.entries)
            val warnings = suspiciousDetector.detect(groups)
            state = SlowlogState.Loaded(snapshot = snapshot, groups = groups, warnings = warnings)
        }
    }

    private fun toggleViewMode() {
        val loaded = state as? SlowlogState.Loaded ?: return
        val newMode = when (loaded.viewMode) {
            SlowlogViewMode.LIST -> SlowlogViewMode.GROUPED
            SlowlogViewMode.GROUPED -> SlowlogViewMode.LIST
            SlowlogViewMode.WARNINGS -> SlowlogViewMode.LIST
        }
        state = loaded.copy(viewMode = newMode, selectedIndex = 0, scrollOffset = 0)
    }

    private fun toggleWarningsMode() {
        val loaded = state as? SlowlogState.Loaded ?: return
        val newMode = if (loaded.viewMode == SlowlogViewMode.WARNINGS) {
            SlowlogViewMode.LIST
        } else {
            SlowlogViewMode.WARNINGS
        }
        state = loaded.copy(viewMode = newMode, selectedIndex = 0, scrollOffset = 0)
    }

    // ─── Scroll ───────────────────────────────────────────────────────────────

    private fun scrollDown() {
        val loaded = state as? SlowlogState.Loaded ?: return
        val size = when (loaded.viewMode) {
            SlowlogViewMode.LIST -> loaded.snapshot.entries.size
            SlowlogViewMode.GROUPED -> loaded.groups.size
            SlowlogViewMode.WARNINGS -> loaded.warnings.size
        }
        val maxIndex = (size - 1).coerceAtLeast(0)
        val newIndex = (loaded.selectedIndex + 1).coerceAtMost(maxIndex)
        val newScroll = adjustScroll(newIndex, loaded.scrollOffset)
        state = loaded.copy(selectedIndex = newIndex, scrollOffset = newScroll)
    }

    private fun scrollUp() {
        val loaded = state as? SlowlogState.Loaded ?: return
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
        val panelRect = TuiRect(left = 2, top = 1, width = 76, height = 23)
        Panel.draw(context = context, rect = panelRect)

        drawHeader(context, panelRect)

        when (val s = state) {
            is SlowlogState.Loading -> drawLoading(context, panelRect)
            is SlowlogState.Error -> drawError(context, panelRect, s.message)
            is SlowlogState.Loaded -> when (s.viewMode) {
                SlowlogViewMode.LIST -> drawEntries(context, panelRect, s)
                SlowlogViewMode.GROUPED -> drawGroups(context, panelRect, s)
                SlowlogViewMode.WARNINGS -> drawWarnings(context, panelRect, s)
            }
        }

        drawFooter(context, panelRect)
    }

    private fun drawHeader(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Slowlog Viewer",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        val subtitle = when (val s = state) {
            is SlowlogState.Loaded -> when (s.viewMode) {
                SlowlogViewMode.LIST -> {
                    val total = s.snapshot.totalEntries
                    val shown = s.snapshot.entries.size
                    val warnSuffix = if (s.warnings.isNotEmpty()) "  ! ${s.warnings.size} warnings" else ""
                    "Showing $shown of $total slowlog entries$warnSuffix"
                }
                SlowlogViewMode.GROUPED -> {
                    val cmdCount = s.groups.size
                    val entryCount = s.snapshot.entries.size
                    val warnSuffix = if (s.warnings.isNotEmpty()) "  ! ${s.warnings.size} warnings" else ""
                    "$cmdCount unique commands from $entryCount entries  [grouped]$warnSuffix"
                }
                SlowlogViewMode.WARNINGS -> {
                    "${s.warnings.size} suspicious command warnings detected  [warnings]"
                }
            }
            is SlowlogState.Loading -> "Loading slowlog entries..."
            is SlowlogState.Error -> "Failed to load slowlog entries"
        }

        context.putText(
            column = panel.left + 3,
            row = panel.top + 2,
            text = subtitle,
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawColumnHeaders(context: TuiContext, panel: TuiRect) {
        val row = panel.top + 4
        val bg = context.theme.panel

        context.putText(
            column = COL_ID,
            row = row,
            text = "#ID  ",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )
        context.putText(
            column = COL_DURATION,
            row = row,
            text = "Duration  ",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )
        context.putText(
            column = COL_RISK,
            row = row,
            text = "Risk  ",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )
        context.putText(
            column = COL_COMMAND,
            row = row,
            text = "Command       ",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )
        context.putText(
            column = COL_ARGS,
            row = row,
            text = "Arguments        ",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )
        context.putText(
            column = COL_CLIENT,
            row = row,
            text = "Client     ",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )

        val separatorRow = panel.top + 5
        val separator = "─".repeat(panel.width - 4)
        context.putText(
            column = panel.left + 2,
            row = separatorRow,
            text = separator,
            foregroundColor = context.theme.border,
            backgroundColor = bg,
        )
    }

    private fun drawLoading(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 7,
            text = "Fetching slowlog from Redis...",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawError(context: TuiContext, panel: TuiRect, message: String) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 7,
            text = "Error: ${message.take(60)}",
            foregroundColor = context.theme.danger,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 9,
            text = "Press r to retry.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawEntries(context: TuiContext, panel: TuiRect, loaded: SlowlogState.Loaded) {
        drawColumnHeaders(context, panel)

        val entries = loaded.snapshot.entries
        if (entries.isEmpty()) {
            context.putText(
                column = panel.left + 3,
                row = panel.top + 7,
                text = "No slowlog entries found. Slowlog threshold may be too high.",
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            return
        }

        val visibleEntries = entries.drop(loaded.scrollOffset).take(VISIBLE_ROWS)
        visibleEntries.forEachIndexed { relativeIndex, entry ->
            val absoluteIndex = loaded.scrollOffset + relativeIndex
            val row = panel.top + FIRST_ENTRY_ROW + relativeIndex
            val isSelected = absoluteIndex == loaded.selectedIndex
            drawEntryRow(context, row, entry, isSelected)
        }

        drawScrollIndicator(context, panel, loaded)
    }

    private fun drawEntryRow(
        context: TuiContext,
        row: Int,
        entry: RedisSlowlogEntry,
        isSelected: Boolean,
    ) {
        val assessment = riskClassifier.classify(entry)
        val bg = if (isSelected) context.theme.border else context.theme.panel
        val fgDefault = if (isSelected) context.theme.panel else context.theme.value
        val fgLabel = if (isSelected) context.theme.panel else context.theme.label
        val fgDuration = if (isSelected) context.theme.panel else riskLevelColor(assessment.level, context)
        val fgRisk = if (isSelected) context.theme.panel else riskLevelColor(assessment.level, context)

        // Fill row background
        context.putText(
            column = COL_ID,
            row = row,
            text = " ".repeat(72),
            foregroundColor = fgDefault,
            backgroundColor = bg,
        )

        // #ID
        context.putText(
            column = COL_ID,
            row = row,
            text = "#${entry.id}".take(WIDTH_ID).padEnd(WIDTH_ID),
            foregroundColor = fgLabel,
            backgroundColor = bg,
        )

        // Duration
        context.putText(
            column = COL_DURATION,
            row = row,
            text = formatDuration(entry.durationMicroseconds).padEnd(WIDTH_DURATION),
            foregroundColor = fgDuration,
            backgroundColor = bg,
        )

        // Risk badge
        context.putText(
            column = COL_RISK,
            row = row,
            text = riskBadge(assessment.level).padEnd(WIDTH_RISK),
            foregroundColor = fgRisk,
            backgroundColor = bg,
            bold = assessment.level == SlowlogRiskLevel.CRITICAL || assessment.level == SlowlogRiskLevel.HIGH,
        )

        // Command
        context.putText(
            column = COL_COMMAND,
            row = row,
            text = entry.command.take(WIDTH_COMMAND).padEnd(WIDTH_COMMAND),
            foregroundColor = fgDefault,
            backgroundColor = bg,
            bold = isSelected,
        )

        // Arguments preview
        val argsText = formatArgsPreview(entry.arguments)
        context.putText(
            column = COL_ARGS,
            row = row,
            text = argsText.take(WIDTH_ARGS).padEnd(WIDTH_ARGS),
            foregroundColor = fgLabel,
            backgroundColor = bg,
        )

        // Client (name if set, else address)
        val clientText = formatClient(entry)
        context.putText(
            column = COL_CLIENT,
            row = row,
            text = clientText.take(WIDTH_CLIENT).padEnd(WIDTH_CLIENT),
            foregroundColor = fgLabel,
            backgroundColor = bg,
        )
    }

    private fun drawGroups(context: TuiContext, panel: TuiRect, loaded: SlowlogState.Loaded) {
        drawGroupColumnHeaders(context, panel)

        val groups = loaded.groups
        if (groups.isEmpty()) {
            context.putText(
                column = panel.left + 3,
                row = panel.top + 7,
                text = "No grouped entries. Press g to switch back to list view.",
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            return
        }

        val visibleGroups = groups.drop(loaded.scrollOffset).take(VISIBLE_ROWS)
        visibleGroups.forEachIndexed { relativeIndex, group ->
            val absoluteIndex = loaded.scrollOffset + relativeIndex
            val row = panel.top + FIRST_ENTRY_ROW + relativeIndex
            val isSelected = absoluteIndex == loaded.selectedIndex
            drawGroupRow(context, row, group, isSelected)
        }

        drawScrollIndicator(context, panel, loaded)
    }

    private fun drawGroupColumnHeaders(context: TuiContext, panel: TuiRect) {
        val row = panel.top + 4
        val bg = context.theme.panel

        context.putText(
            column = GRP_RISK,
            row = row,
            text = "Risk ",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )
        context.putText(
            column = GRP_COUNT,
            row = row,
            text = "Count ",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )
        context.putText(
            column = GRP_COMMAND,
            row = row,
            text = "Command      ",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )
        context.putText(
            column = GRP_TOTAL,
            row = row,
            text = "Total     ",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )
        context.putText(
            column = GRP_AVG,
            row = row,
            text = "Avg       ",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )
        context.putText(
            column = GRP_MAX,
            row = row,
            text = "Max       ",
            foregroundColor = context.theme.label,
            backgroundColor = bg,
        )

        val separatorRow = panel.top + 5
        val separator = "─".repeat(panel.width - 4)
        context.putText(
            column = panel.left + 2,
            row = separatorRow,
            text = separator,
            foregroundColor = context.theme.border,
            backgroundColor = bg,
        )
    }

    private fun drawGroupRow(
        context: TuiContext,
        row: Int,
        group: io.github.eyuppastirmaci.dioptra.domain.slowlog.SlowlogCommandGroup,
        isSelected: Boolean,
    ) {
        val bg = if (isSelected) context.theme.border else context.theme.panel
        val fgDefault = if (isSelected) context.theme.panel else context.theme.value
        val fgLabel = if (isSelected) context.theme.panel else context.theme.label
        val fgRisk = if (isSelected) context.theme.panel else riskLevelColor(group.worstRiskLevel, context)

        // Fill row background
        context.putText(
            column = GRP_RISK,
            row = row,
            text = " ".repeat(72),
            foregroundColor = fgDefault,
            backgroundColor = bg,
        )

        // Risk badge
        context.putText(
            column = GRP_RISK,
            row = row,
            text = riskBadge(group.worstRiskLevel).padEnd(WIDTH_GRP_RISK),
            foregroundColor = fgRisk,
            backgroundColor = bg,
            bold = group.worstRiskLevel == SlowlogRiskLevel.CRITICAL || group.worstRiskLevel == SlowlogRiskLevel.HIGH,
        )

        // Occurrence count
        val countText = "×${group.occurrences}"
        context.putText(
            column = GRP_COUNT,
            row = row,
            text = countText.padEnd(WIDTH_GRP_COUNT),
            foregroundColor = if (isSelected) fgDefault else if (group.occurrences > 10) context.theme.warning else context.theme.value,
            backgroundColor = bg,
        )

        // Command
        context.putText(
            column = GRP_COMMAND,
            row = row,
            text = group.command.take(WIDTH_GRP_COMMAND).padEnd(WIDTH_GRP_COMMAND),
            foregroundColor = fgDefault,
            backgroundColor = bg,
            bold = isSelected,
        )

        // Total duration
        context.putText(
            column = GRP_TOTAL,
            row = row,
            text = formatDuration(group.totalDurationMicroseconds).padEnd(WIDTH_GRP_DURATION),
            foregroundColor = fgLabel,
            backgroundColor = bg,
        )

        // Avg duration
        context.putText(
            column = GRP_AVG,
            row = row,
            text = formatDuration(group.avgDurationMicroseconds).padEnd(WIDTH_GRP_DURATION),
            foregroundColor = fgLabel,
            backgroundColor = bg,
        )

        // Max duration
        context.putText(
            column = GRP_MAX,
            row = row,
            text = formatDuration(group.maxDurationMicroseconds).padEnd(WIDTH_GRP_DURATION),
            foregroundColor = fgRisk,
            backgroundColor = bg,
        )
    }

    private fun drawWarnings(context: TuiContext, panel: TuiRect, loaded: SlowlogState.Loaded) {
        val warnings = loaded.warnings

        // Section header
        val headerRow = panel.top + 4
        context.putText(
            column = panel.left + 3,
            row = headerRow,
            text = "Suspicious Command Warnings",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
            bold = true,
        )
        val separator = "─".repeat(panel.width - 4)
        context.putText(
            column = panel.left + 2,
            row = panel.top + 5,
            text = separator,
            foregroundColor = context.theme.border,
            backgroundColor = context.theme.panel,
        )

        if (warnings.isEmpty()) {
            context.putText(
                column = panel.left + 3,
                row = panel.top + 7,
                text = "No suspicious commands detected. Your slowlog looks clean.",
                foregroundColor = context.theme.success,
                backgroundColor = context.theme.panel,
            )
            return
        }

        val visibleWarnings = warnings.drop(loaded.scrollOffset).take(VISIBLE_ROWS)
        visibleWarnings.forEachIndexed { relativeIndex, warning ->
            val absoluteIndex = loaded.scrollOffset + relativeIndex
            val baseRow = panel.top + FIRST_ENTRY_ROW + relativeIndex
            val isSelected = absoluteIndex == loaded.selectedIndex

            val bg = if (isSelected) context.theme.border else context.theme.panel
            val fgBadge = if (isSelected) context.theme.panel else riskLevelColor(warning.severity, context)
            val fgText = if (isSelected) context.theme.panel else context.theme.value

            // Fill row background
            context.putText(
                column = panel.left + 3,
                row = baseRow,
                text = " ".repeat(panel.width - 6),
                foregroundColor = fgText,
                backgroundColor = bg,
            )

            // Severity badge
            context.putText(
                column = panel.left + 3,
                row = baseRow,
                text = "[${riskBadge(warning.severity)}]",
                foregroundColor = fgBadge,
                backgroundColor = bg,
                bold = warning.severity == SlowlogRiskLevel.CRITICAL || warning.severity == SlowlogRiskLevel.HIGH,
            )

            // Command label
            context.putText(
                column = panel.left + 10,
                row = baseRow,
                text = warning.command.padEnd(10),
                foregroundColor = if (isSelected) context.theme.panel else context.theme.title,
                backgroundColor = bg,
                bold = true,
            )

            // Warning message (truncated to fit panel)
            val maxMsgWidth = panel.width - 21
            val msgText = if (warning.message.length > maxMsgWidth) {
                "${warning.message.take(maxMsgWidth - 1)}~"
            } else {
                warning.message
            }
            context.putText(
                column = panel.left + 21,
                row = baseRow,
                text = msgText,
                foregroundColor = fgText,
                backgroundColor = bg,
            )
        }

        drawScrollIndicator(context, panel, loaded)
    }

    private fun drawScrollIndicator(context: TuiContext, panel: TuiRect, loaded: SlowlogState.Loaded) {
        val size = when (loaded.viewMode) {
            SlowlogViewMode.LIST -> loaded.snapshot.entries.size
            SlowlogViewMode.GROUPED -> loaded.groups.size
            SlowlogViewMode.WARNINGS -> loaded.warnings.size
        }
        if (size <= VISIBLE_ROWS) return

        val indicatorRow = panel.top + FIRST_ENTRY_ROW + VISIBLE_ROWS
        val text = "↑↓ scroll  (${loaded.selectedIndex + 1}/$size)"
        context.putText(
            column = panel.left + 3,
            row = indicatorRow,
            text = text,
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawFooter(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + panel.height - 2,
            text = fitToPanelWidth(panel, "g:group  w:warn  r:refresh  b/esc:back  q:exit"),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    // ─── Formatting ───────────────────────────────────────────────────────────

    private fun formatDuration(microseconds: Long): String {
        return when {
            microseconds < 1_000L -> "${microseconds}µs"
            microseconds < 1_000_000L -> "${"%.1f".format(microseconds / 1_000.0)}ms"
            else -> "${"%.2f".format(microseconds / 1_000_000.0)}s"
        }
    }

    private fun riskLevelColor(level: SlowlogRiskLevel, context: TuiContext): TextColor {
        return when (level) {
            SlowlogRiskLevel.CRITICAL -> context.theme.danger
            SlowlogRiskLevel.HIGH -> context.theme.warning
            SlowlogRiskLevel.MEDIUM -> context.theme.hint
            SlowlogRiskLevel.LOW -> context.theme.value
        }
    }

    private fun riskBadge(level: SlowlogRiskLevel): String {
        return when (level) {
            SlowlogRiskLevel.CRITICAL -> "CRIT"
            SlowlogRiskLevel.HIGH -> "HIGH"
            SlowlogRiskLevel.MEDIUM -> "MED "
            SlowlogRiskLevel.LOW -> "LOW "
        }
    }

    private fun formatArgsPreview(arguments: List<String>): String {
        if (arguments.isEmpty()) return ""
        val joined = arguments.joinToString(" ")
        return if (joined.length <= WIDTH_ARGS) joined else "${joined.take(WIDTH_ARGS - 1)}~"
    }

    private fun formatClient(entry: RedisSlowlogEntry): String {
        return when {
            !entry.clientName.isNullOrBlank() -> entry.clientName
            !entry.clientAddress.isNullOrBlank() -> entry.clientAddress
            else -> "-"
        }
    }

    private fun fitToPanelWidth(panel: TuiRect, text: String): String {
        val maxWidth = (panel.width - 6).coerceAtLeast(0)
        return text.take(maxWidth)
    }

    @Suppress("unused")
    private fun formatTimestamp(seconds: Long): String {
        return try {
            val instant = Instant.ofEpochSecond(seconds)
            TIMESTAMP_FORMATTER.format(instant)
        } catch (e: Exception) {
            seconds.toString()
        }
    }

    // ─── Key helpers ──────────────────────────────────────────────────────────

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
        // Column absolute positions
        const val COL_ID = 5
        const val COL_DURATION = 11
        const val COL_RISK = 22
        const val COL_COMMAND = 28
        const val COL_ARGS = 43
        const val COL_CLIENT = 62

        // Column widths
        const val WIDTH_ID = 5
        const val WIDTH_DURATION = 10
        const val WIDTH_RISK = 5
        const val WIDTH_COMMAND = 14
        const val WIDTH_ARGS = 18
        const val WIDTH_CLIENT = 12

        const val FIRST_ENTRY_ROW = 6   // panel.top + 6
        const val VISIBLE_ROWS = 13

        // Grouped view column positions
        const val GRP_RISK = 5
        const val GRP_COUNT = 11
        const val GRP_COMMAND = 18
        const val GRP_TOTAL = 33
        const val GRP_AVG = 44
        const val GRP_MAX = 55

        // Grouped view column widths
        const val WIDTH_GRP_RISK = 5
        const val WIDTH_GRP_COUNT = 6
        const val WIDTH_GRP_COMMAND = 14
        const val WIDTH_GRP_DURATION = 10

        val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
    }
}
