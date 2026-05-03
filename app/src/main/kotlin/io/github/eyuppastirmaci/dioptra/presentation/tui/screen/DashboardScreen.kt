package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysUseCase
import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.MetricRow
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect

class DashboardScreen(
    private val snapshot: RedisDashboardSnapshot,
    private val browseKeysUseCase: BrowseKeysUseCase,
) : TuiScreen {

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

            isCharacter(keyStroke, 'k') -> {
                TuiScreenResult.Navigate(
                    nextScreen = KeyBrowserScreen(
                        browseKeysUseCase = browseKeysUseCase,
                    )
                )
            }

            else -> TuiScreenResult.Continue
        }
    }

    private fun drawDashboardPanel(context: TuiContext) {
        val panelRect = TuiRect(
            left = 4,
            top = 2,
            width = 72,
            height = 17,
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

        MetricRow.draw(
            context = context,
            row = panelRect.top + 5,
            label = "Status",
            value = snapshot.status,
            valueForegroundColor = if (snapshot.status == "Connected") {
                context.theme.success
            } else {
                context.theme.value
            },
        )

        MetricRow.draw(
            context = context,
            row = panelRect.top + 6,
            label = "Redis version",
            value = snapshot.redisVersion,
        )

        MetricRow.draw(
            context = context,
            row = panelRect.top + 7,
            label = "Used memory",
            value = snapshot.usedMemoryHuman,
        )

        MetricRow.draw(
            context = context,
            row = panelRect.top + 8,
            label = "Connected clients",
            value = snapshot.connectedClients.toString(),
        )

        MetricRow.draw(
            context = context,
            row = panelRect.top + 9,
            label = "Commands processed",
            value = snapshot.totalCommandsProcessed.toString(),
        )

        MetricRow.draw(
            context = context,
            row = panelRect.top + 10,
            label = "Keyspace hits",
            value = snapshot.keyspaceHits.toString(),
        )

        MetricRow.draw(
            context = context,
            row = panelRect.top + 11,
            label = "Keyspace misses",
            value = snapshot.keyspaceMisses.toString(),
        )

        MetricRow.draw(
            context = context,
            row = panelRect.top + 12,
            label = "Total keys",
            value = snapshot.totalKeys.toString(),
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 15,
            text = "k: key browser   q/ESC: exit",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
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
}