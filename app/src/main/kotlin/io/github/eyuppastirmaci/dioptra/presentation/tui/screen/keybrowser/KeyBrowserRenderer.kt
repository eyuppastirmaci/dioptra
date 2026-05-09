package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser

import com.googlecode.lanterna.TextColor
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyBrowserPage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeySummary
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.PanelText
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeyBrowserSorter
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeyMemoryUsageFormatter
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeyRiskClassifier
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeyTtlFormatter
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.TextTruncator

class KeyBrowserRenderer(
    private val sorter: RedisKeyBrowserSorter,
    private val ttlFormatter: RedisKeyTtlFormatter,
    private val memoryUsageFormatter: RedisKeyMemoryUsageFormatter,
    private val riskClassifier: RedisKeyRiskClassifier,
    private val textTruncator: TextTruncator,
) {

    fun render(
        context: TuiContext,
        renderState: KeyBrowserRenderState,
    ) {
        val panelRect = TuiRect(
            left = 2,
            top = 1,
            width = 84,
            height = 21,
        )

        Panel.draw(
            context = context,
            rect = panelRect,
        )

        drawTitle(context, panelRect)
        drawMetadata(context, panelRect, renderState)

        if (renderState.inputMode == KeyBrowserInputMode.PatternSearch) {
            drawPatternInput(context, panelRect, renderState.patternInput)
        } else {
            when (val currentState = renderState.state) {
                is KeyBrowserState.Loading -> drawLoading(context, panelRect, currentState.cursor)
                is KeyBrowserState.Loaded -> drawLoadedPage(context, panelRect, renderState, currentState.page)
                is KeyBrowserState.Error -> drawError(context, panelRect, currentState.message)
                is KeyBrowserState.Cancelled -> drawCancelled(context, panelRect, currentState.cursor)
            }
        }

        drawFooter(context, panelRect, renderState)
    }

    private fun drawTitle(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 1,
            text = "Dioptra Key Browser",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 2,
            text = "SCAN-based Redis key inspection",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawMetadata(
        context: TuiContext,
        panelRect: TuiRect,
        renderState: KeyBrowserRenderState,
    ) {
        val loadedPage = (renderState.state as? KeyBrowserState.Loaded)?.page
        val cursorText = if (loadedPage == null) {
            "-"
        } else {
            "${loadedPage.cursor} -> ${loadedPage.nextCursor}"
        }
        val hasMoreText = if (loadedPage?.hasMore == true) "yes" else "no"

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 4,
            text = "Pattern: ${renderState.pattern}",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 25,
            row = panelRect.top + 4,
            text = "Count: ${renderState.count}",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 42,
            row = panelRect.top + 4,
            text = "Cursor: $cursorText",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 68,
            row = panelRect.top + 4,
            text = "More: $hasMoreText",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 5,
            text = truncate("Sort: ${renderState.sortMode.label}", 76),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawPatternInput(
        context: TuiContext,
        panelRect: TuiRect,
        patternInput: String,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 6,
            text = "Search pattern: ${truncate(patternInput, PATTERN_INPUT_WIDTH)}_".padEnd(78),
            foregroundColor = context.theme.value,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 7,
            text = "Enter: apply   ESC: cancel   empty: *".padEnd(78),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawTableHeader(
        context: TuiContext,
    ) {
        context.putText(
            column = KEY_COLUMN,
            row = TABLE_HEADER_ROW,
            text = "KEY".padEnd(KEY_WIDTH),
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = TYPE_COLUMN,
            row = TABLE_HEADER_ROW,
            text = "TYPE".padEnd(TYPE_WIDTH),
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = TTL_COLUMN,
            row = TABLE_HEADER_ROW,
            text = "TTL".padEnd(TTL_WIDTH),
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = MEMORY_COLUMN,
            row = TABLE_HEADER_ROW,
            text = "MEMORY".padEnd(MEMORY_WIDTH),
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )
    }

    private fun drawLoadedPage(
        context: TuiContext,
        panelRect: TuiRect,
        renderState: KeyBrowserRenderState,
        page: RedisKeyBrowserPage,
    ) {
        if (page.keys.isEmpty()) {
            drawEmptyState(context, panelRect, page)
            return
        }

        val sortedKeys = sorter.sort(page.keys, renderState.sortMode)

        drawTableHeader(context)
        drawRows(context, sortedKeys, renderState.selectedKeyIndex)

        if (!page.hasMore) {
            drawEndOfResults(context, panelRect)
        }
    }

    private fun drawRows(
        context: TuiContext,
        keys: List<RedisKeySummary>,
        selectedIndex: Int,
    ) {
        keys
            .take(MAX_VISIBLE_KEYS)
            .forEachIndexed { index, key ->
                val row = FIRST_KEY_ROW + index
                val selected = index == selectedIndex

                context.putText(
                    column = KEY_COLUMN,
                    row = row,
                    text = formatKeyName(key.name, selected).padEnd(KEY_WIDTH),
                    foregroundColor = if (selected) context.theme.success else context.theme.value,
                    backgroundColor = context.theme.panel,
                    bold = selected,
                )

                context.putText(
                    column = TYPE_COLUMN,
                    row = row,
                    text = key.type.name.lowercase().padEnd(TYPE_WIDTH),
                    foregroundColor = if (selected) context.theme.value else context.theme.label,
                    backgroundColor = context.theme.panel,
                    bold = selected,
                )

                context.putText(
                    column = TTL_COLUMN,
                    row = row,
                    text = ttlFormatter.format(key.ttl).padEnd(TTL_WIDTH),
                    foregroundColor = ttlForegroundColor(context, key.ttl, selected),
                    backgroundColor = context.theme.panel,
                    bold = selected || key.ttl == RedisKeyTtlStatus.NoExpiration,
                )

                context.putText(
                    column = MEMORY_COLUMN,
                    row = row,
                    text = memoryUsageFormatter.format(key.memoryUsage).padEnd(MEMORY_WIDTH),
                    foregroundColor = memoryForegroundColor(context, key.memoryUsage, selected),
                    backgroundColor = context.theme.panel,
                    bold = selected || riskClassifier.isBigKey(key.memoryUsage),
                )
            }
    }

    private fun drawLoading(
        context: TuiContext,
        panelRect: TuiRect,
        cursor: String,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 8,
            text = "Loading keys...",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 10,
            text = "Scanning cursor $cursor. Press ESC to cancel.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawCancelled(
        context: TuiContext,
        panelRect: TuiRect,
        cursor: String,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 8,
            text = "Scan cancelled at cursor $cursor.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 10,
            text = "Press r to retry, b/ESC to return, or q to exit.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawEmptyState(
        context: TuiContext,
        panelRect: TuiRect,
        page: RedisKeyBrowserPage,
    ) {
        val message = if (page.hasMore) {
            "No keys returned for this scan page."
        } else if (page.pattern == DEFAULT_PATTERN) {
            "No keys found in the selected database."
        } else {
            "No keys match pattern '${page.pattern}'."
        }

        val hint = if (page.hasMore) {
            "Press n to continue scanning from cursor ${page.nextCursor}."
        } else {
            "Press r to refresh, b/ESC to return, or q to exit."
        }

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 8,
            text = truncate(message, 76),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 10,
            text = truncate(hint, 76),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawEndOfResults(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        val row = FIRST_KEY_ROW + MAX_VISIBLE_KEYS
        context.fillRect(
            rect = TuiRect(
                left = panelRect.left + 1,
                top = row,
                width = panelRect.width - 2,
                height = 1,
            ),
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panelRect.left + 3,
            row = row,
            text = "End of keyspace reached.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawError(
        context: TuiContext,
        panelRect: TuiRect,
        message: String,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 8,
            text = "Failed to browse keys:",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 10,
            text = truncate(message, 76),
            foregroundColor = context.theme.warning,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 12,
            text = "Press r to retry, b/ESC to return, or q to exit.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawFooter(
        context: TuiContext,
        panelRect: TuiRect,
        renderState: KeyBrowserRenderState,
    ) {
        val footerTopRow = panelRect.top + panelRect.height - 3
        val footerBottomRow = panelRect.top + panelRect.height - 2
        PanelText.clearLine(context, panelRect, footerTopRow)
        PanelText.clearLine(context, panelRect, footerBottomRow)

        val column = panelRect.left + 3

        when {
            renderState.inputMode == KeyBrowserInputMode.PatternSearch -> {
                context.putText(
                    column = column,
                    row = footerBottomRow,
                    text = truncate("pattern search mode", FOOTER_TEXT_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
            }

            renderState.isLoading -> {
                context.putText(
                    column = column,
                    row = footerBottomRow,
                    text = truncate("ESC: cancel scan   q: exit", FOOTER_TEXT_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
            }

            renderState.canReturnBack -> {
                context.putText(
                    column = column,
                    row = footerTopRow,
                    text = truncate("Enter: detail  Up/Down  / search  m/t/l/u sort  n/r", FOOTER_TEXT_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
                context.putText(
                    column = column,
                    row = footerBottomRow,
                    text = truncate("b/ESC: dashboard", FOOTER_TEXT_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
            }

            else -> {
                context.putText(
                    column = column,
                    row = footerTopRow,
                    text = truncate("Enter: detail  Up/Down  / search  m/t/l/u sort  n/r", FOOTER_TEXT_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
                context.putText(
                    column = column,
                    row = footerBottomRow,
                    text = truncate("q/ESC: exit", FOOTER_TEXT_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
            }
        }
    }

    private fun ttlForegroundColor(
        context: TuiContext,
        ttl: RedisKeyTtlStatus,
        selected: Boolean,
    ): TextColor {
        return when {
            ttl == RedisKeyTtlStatus.NoExpiration -> context.theme.warning
            selected -> context.theme.value
            else -> context.theme.label
        }
    }

    private fun memoryForegroundColor(
        context: TuiContext,
        memoryUsage: RedisKeyMemoryUsage,
        selected: Boolean,
    ): TextColor {
        return when {
            riskClassifier.isBigKey(memoryUsage) -> context.theme.warning
            selected -> context.theme.value
            else -> context.theme.label
        }
    }

    private fun formatKeyName(
        name: String,
        selected: Boolean,
    ): String {
        val marker = if (selected) "> " else "  "
        return marker + truncate(name, KEY_WIDTH - marker.length)
    }

    private fun truncate(
        value: String,
        maxLength: Int,
    ): String {
        return textTruncator.truncate(value, maxLength)
    }

    companion object {
        const val INITIAL_CURSOR = "0"
        const val DEFAULT_PATTERN = "*"
        const val DEFAULT_COUNT = 20L
        const val MAX_PATTERN_LENGTH = 72
        const val MAX_VISIBLE_KEYS = 9

        private const val PATTERN_INPUT_WIDTH = 56
        private const val TABLE_HEADER_ROW = 7
        private const val FIRST_KEY_ROW = 9

        private const val KEY_COLUMN = 5
        private const val TYPE_COLUMN = 45
        private const val TTL_COLUMN = 57
        private const val MEMORY_COLUMN = 68

        private const val KEY_WIDTH = 36
        private const val TYPE_WIDTH = 8
        private const val TTL_WIDTH = 10
        private const val MEMORY_WIDTH = 12
        private const val FOOTER_TEXT_WIDTH = 76
    }
}
