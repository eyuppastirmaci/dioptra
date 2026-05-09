package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.namespace.LoadNamespaceDetailUseCase
import io.github.eyuppastirmaci.dioptra.application.namespace.NamespaceBookmarkManager
import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect

class BookmarkedNamespacesScreen(
    private val profileName: String,
    private val bookmarkManager: NamespaceBookmarkManager,
    private val loadNamespaceDetailUseCase: LoadNamespaceDetailUseCase,
    private val namespaceAnalysisSettings: NamespaceAnalysisSettings,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private var bookmarks: List<String> = bookmarkManager.load(profileName).sorted()
    private var selectedIndex = 0
    private var scrollOffset = 0
    private var message: String? = null

    override fun render(context: TuiContext) {
        context.clearBackground()
        drawPanel(context)
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isExitKey(keyStroke) -> TuiScreenResult.Exit
            isBackKey(keyStroke) -> TuiScreenResult.Navigate(back())
            keyStroke.keyType == KeyType.Enter -> openSelectedBookmark()
            keyStroke.keyType == KeyType.ArrowDown || isCharacter(keyStroke, 'j') -> {
                moveSelection(delta = 1)
                TuiScreenResult.Continue
            }
            keyStroke.keyType == KeyType.ArrowUp || isCharacter(keyStroke, 'k') -> {
                moveSelection(delta = -1)
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, 'd') -> {
                removeSelectedBookmark()
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, 'r') -> {
                reload()
                TuiScreenResult.Continue
            }
            else -> TuiScreenResult.Continue
        }
    }

    private fun openSelectedBookmark(): TuiScreenResult {
        val namespaceName = bookmarks.getOrNull(selectedIndex) ?: return TuiScreenResult.Continue
        return TuiScreenResult.Navigate(
            NamespaceDetailScreen(
                namespaceName = namespaceName,
                loadNamespaceDetailUseCase = loadNamespaceDetailUseCase,
                namespaceAnalysisSettings = namespaceAnalysisSettings,
                back = { this },
            )
        )
    }

    private fun removeSelectedBookmark() {
        val namespaceName = bookmarks.getOrNull(selectedIndex) ?: return
        bookmarks = bookmarkManager.remove(profileName, namespaceName).sorted()
        selectedIndex = selectedIndex.coerceAtMost((bookmarks.size - 1).coerceAtLeast(0))
        scrollOffset = scrollOffset.coerceAtMost(selectedIndex)
        message = "Removed bookmark: $namespaceName"
    }

    private fun reload() {
        bookmarks = bookmarkManager.load(profileName).sorted()
        selectedIndex = selectedIndex.coerceAtMost((bookmarks.size - 1).coerceAtLeast(0))
        scrollOffset = scrollOffset.coerceAtMost(selectedIndex)
        message = "Bookmarks refreshed."
    }

    private fun moveSelection(delta: Int) {
        if (bookmarks.isEmpty()) {
            return
        }

        selectedIndex = (selectedIndex + delta).coerceIn(0, bookmarks.lastIndex)
        scrollOffset = adjustScroll(selectedIndex, scrollOffset)
    }

    private fun adjustScroll(selectedIndex: Int, currentScroll: Int): Int {
        return when {
            selectedIndex < currentScroll -> selectedIndex
            selectedIndex >= currentScroll + VISIBLE_ROWS -> selectedIndex - VISIBLE_ROWS + 1
            else -> currentScroll
        }
    }

    private fun drawPanel(context: TuiContext) {
        val panel = TuiRect(left = 2, top = 1, width = 76, height = 20)
        Panel.draw(context = context, rect = panel)
        drawHeader(context, panel)
        drawBookmarks(context, panel)
        drawFooter(context, panel)
    }

    private fun drawHeader(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Bookmarked Namespaces",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 2,
            text = fit("Profile: $profileName   ${bookmarks.size} bookmarks", panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
        message?.let {
            context.putText(
                column = panel.left + 3,
                row = panel.top + 3,
                text = fit(it, panel.width - 6),
                foregroundColor = context.theme.success,
                backgroundColor = context.theme.panel,
            )
        }
        context.putText(
            column = panel.left + 2,
            row = panel.top + 4,
            text = "─".repeat(panel.width - 4),
            foregroundColor = context.theme.border,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawBookmarks(context: TuiContext, panel: TuiRect) {
        if (bookmarks.isEmpty()) {
            context.putText(
                column = panel.left + 3,
                row = panel.top + FIRST_ROW,
                text = "No bookmarked namespaces yet. Press b in Namespace Analysis to add one.",
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            return
        }

        bookmarks.drop(scrollOffset).take(VISIBLE_ROWS).forEachIndexed { relativeIndex, namespaceName ->
            val absoluteIndex = scrollOffset + relativeIndex
            val row = panel.top + FIRST_ROW + relativeIndex
            val isSelected = absoluteIndex == selectedIndex
            val bg = if (isSelected) context.theme.border else context.theme.panel
            val fg = if (isSelected) context.theme.panel else context.theme.value

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
                text = fit(namespaceName, panel.width - 8),
                foregroundColor = fg,
                backgroundColor = bg,
                bold = isSelected,
            )
        }

        if (bookmarks.size > VISIBLE_ROWS) {
            context.putText(
                column = panel.left + 3,
                row = panel.top + FIRST_ROW + VISIBLE_ROWS,
                text = "↑↓/j/k scroll  (${selectedIndex + 1}/${bookmarks.size})",
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
        }
    }

    private fun drawFooter(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + panel.height - 2,
            text = fit("enter:detail  d:remove  r:refresh  b/esc:back  q:exit", panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
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

    private fun isCharacter(keyStroke: KeyStroke, expected: Char): Boolean {
        return keyStroke.keyType == KeyType.Character &&
            keyStroke.character?.lowercaseChar() == expected
    }

    private companion object {
        const val FIRST_ROW = 6
        const val VISIBLE_ROWS = 10
    }
}
