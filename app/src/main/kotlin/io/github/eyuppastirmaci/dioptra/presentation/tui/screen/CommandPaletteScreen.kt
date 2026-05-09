package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.commandpalette.CommandPaletteItem

class CommandPaletteScreen(
    private val items: List<CommandPaletteItem>,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private var query = ""
    private var selectedIndex = 0
    private var scrollOffset = 0

    override fun render(context: TuiContext) {
        context.clearBackground()
        drawPanel(context)
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isBackKey(keyStroke) -> TuiScreenResult.Navigate(back())

            keyStroke.keyType == KeyType.Enter -> {
                filteredItems().getOrNull(selectedIndex)?.action?.invoke() ?: TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.Backspace -> {
                query = query.dropLast(1)
                resetSelection()
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.ArrowDown || isCharacter(keyStroke, 'j') -> {
                moveSelection(delta = 1)
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.ArrowUp || isCharacter(keyStroke, 'k') -> {
                moveSelection(delta = -1)
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.Character -> {
                appendQueryCharacter(keyStroke.character)
                TuiScreenResult.Continue
            }

            else -> TuiScreenResult.Continue
        }
    }

    private fun drawPanel(context: TuiContext) {
        val panel = TuiRect(left = 2, top = 1, width = 76, height = 22)
        Panel.draw(context = context, rect = panel)
        drawHeader(context, panel)
        drawItems(context, panel)
        drawFooter(context, panel)
    }

    private fun drawHeader(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Command Palette",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 3,
            text = fit("> $query", panel.width - 6),
            foregroundColor = context.theme.value,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 2,
            row = panel.top + 4,
            text = "─".repeat(panel.width - 4),
            foregroundColor = context.theme.border,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawItems(context: TuiContext, panel: TuiRect) {
        val visibleItems = filteredItems()
        if (visibleItems.isEmpty()) {
            context.putText(
                column = panel.left + 3,
                row = panel.top + FIRST_ITEM_ROW,
                text = "No commands match your search.",
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            return
        }

        visibleItems
            .drop(scrollOffset)
            .take(VISIBLE_ROWS)
            .forEachIndexed { relativeIndex, item ->
                val absoluteIndex = scrollOffset + relativeIndex
                val row = panel.top + FIRST_ITEM_ROW + relativeIndex
                drawItemRow(context, panel, row, item, absoluteIndex == selectedIndex)
            }

        if (visibleItems.size > VISIBLE_ROWS) {
            context.putText(
                column = panel.left + 3,
                row = panel.top + FIRST_ITEM_ROW + VISIBLE_ROWS,
                text = "↑↓/j/k scroll  (${selectedIndex + 1}/${visibleItems.size})",
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
        }
    }

    private fun drawItemRow(
        context: TuiContext,
        panel: TuiRect,
        row: Int,
        item: CommandPaletteItem,
        isSelected: Boolean,
    ) {
        val bg = if (isSelected) context.theme.border else context.theme.panel
        val fg = if (isSelected) context.theme.panel else context.theme.value
        val fgHint = if (isSelected) context.theme.panel else context.theme.hint

        context.putText(
            column = panel.left + 2,
            row = row,
            text = " ".repeat(panel.width - 4),
            foregroundColor = fg,
            backgroundColor = bg,
        )
        context.putText(
            column = panel.left + 3,
            row = row,
            text = fit(item.title, TITLE_WIDTH).padEnd(TITLE_WIDTH),
            foregroundColor = fg,
            backgroundColor = bg,
            bold = isSelected,
        )
        context.putText(
            column = panel.left + 30,
            row = row,
            text = fit(item.description, DESCRIPTION_WIDTH).padEnd(DESCRIPTION_WIDTH),
            foregroundColor = fgHint,
            backgroundColor = bg,
        )
        item.shortcut?.let { shortcut ->
            context.putText(
                column = panel.left + 65,
                row = row,
                text = fit(shortcut, SHORTCUT_WIDTH),
                foregroundColor = fgHint,
                backgroundColor = bg,
            )
        }
    }

    private fun drawFooter(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + panel.height - 2,
            text = fit("type:filter  enter:run  backspace:edit  esc:back", panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun filteredItems(): List<CommandPaletteItem> {
        return items.filter { it.matches(query) }
    }

    private fun appendQueryCharacter(character: Char?) {
        if (character == null || character.isISOControl()) {
            return
        }

        if (query.length >= MAX_QUERY_LENGTH) {
            return
        }

        query += character
        resetSelection()
    }

    private fun moveSelection(delta: Int) {
        val itemCount = filteredItems().size
        if (itemCount == 0) {
            return
        }

        val newIndex = (selectedIndex + delta).coerceIn(0, itemCount - 1)
        selectedIndex = newIndex
        scrollOffset = adjustScroll(newIndex, scrollOffset)
    }

    private fun resetSelection() {
        selectedIndex = 0
        scrollOffset = 0
    }

    private fun adjustScroll(selectedIndex: Int, currentScroll: Int): Int {
        return when {
            selectedIndex < currentScroll -> selectedIndex
            selectedIndex >= currentScroll + VISIBLE_ROWS -> selectedIndex - VISIBLE_ROWS + 1
            else -> currentScroll
        }
    }

    private fun fit(text: String, maxWidth: Int): String {
        return text.take(maxWidth.coerceAtLeast(0))
    }

    private fun isBackKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.Escape
    }

    private fun isCharacter(keyStroke: KeyStroke, expected: Char): Boolean {
        return keyStroke.keyType == KeyType.Character &&
            keyStroke.character?.lowercaseChar() == expected
    }

    private companion object {
        const val FIRST_ITEM_ROW = 6
        const val VISIBLE_ROWS = 12
        const val MAX_QUERY_LENGTH = 64
        const val TITLE_WIDTH = 25
        const val DESCRIPTION_WIDTH = 32
        const val SHORTCUT_WIDTH = 8
    }
}
