package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysUseCase
import io.github.eyuppastirmaci.dioptra.application.key.LoadKeyDetailUseCase
import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.MetricRow
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeyBrowserSorter
import io.github.eyuppastirmaci.dioptra.presentation.tui.input.TuiKeyMatcher
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser.KeyBrowserRenderer

class DashboardScreen(
    private val snapshot: RedisDashboardSnapshot,
    private val browseKeysUseCase: BrowseKeysUseCase,
    private val loadKeyDetailUseCase: LoadKeyDetailUseCase,
    private val keyBrowserRenderer: KeyBrowserRenderer,
    private val keyBrowserSorter: RedisKeyBrowserSorter,
    private val keyMatcher: TuiKeyMatcher,
    private val disconnect: (() -> TuiScreen)? = null,
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
                        loadKeyDetailUseCase = loadKeyDetailUseCase,
                        renderer = keyBrowserRenderer,
                        sorter = keyBrowserSorter,
                        keyMatcher = keyMatcher,
                        back = { this },
                    )
                )
            }

            isCharacter(keyStroke, 'd') && disconnect != null -> {
                TuiScreenResult.Navigate(
                    nextScreen = disconnect.invoke(),
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
            height = 19,
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

        drawLeftMetric(
            context = context,
            row = panelRect.top + 5,
            label = "Status",
            value = snapshot.status,
            valueForegroundColor = if (snapshot.status == "Connected") context.theme.success else context.theme.value,
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 5,
            label = "Profile",
            value = snapshot.activeConnectionName,
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 6,
            label = "Database",
            value = snapshot.selectedDatabase.toString(),
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 6,
            label = "Server",
            value = snapshot.redisVersion,
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 7,
            label = "Uptime",
            value = snapshot.uptime,
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 7,
            label = "Used memory",
            value = snapshot.usedMemoryHuman,
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 8,
            label = "Mem frag",
            value = snapshot.memoryFragmentationHint,
            valueForegroundColor = if (snapshot.memoryFragmentationHealthy) context.theme.value else context.theme.warning,
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 8,
            label = "Clients",
            value = "${snapshot.connectedClients} ${snapshot.connectedClientsWarning}",
            valueForegroundColor = if (snapshot.connectedClientsHealthy) context.theme.value else context.theme.warning,
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 9,
            label = "Blocked",
            value = snapshot.blockedClients.toString(),
            valueForegroundColor = if (snapshot.blockedClients == 0) context.theme.value else context.theme.warning,
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 9,
            label = "Ops/sec",
            value = snapshot.instantaneousOpsPerSecond.toString(),
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 10,
            label = "Commands",
            value = snapshot.totalCommandsProcessed.toString(),
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 10,
            label = "Total keys",
            value = snapshot.totalKeys.toString(),
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 11,
            label = "Key hits",
            value = snapshot.keyspaceHits.toString(),
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 11,
            label = "Key misses",
            value = snapshot.keyspaceMisses.toString(),
        )

        drawLeftMetric(
            context = context,
            row = panelRect.top + 12,
            label = "Maxmemory",
            value = snapshot.maxmemoryPolicy,
        )

        drawRightMetric(
            context = context,
            row = panelRect.top + 12,
            label = "Evicted",
            value = snapshot.evictedKeys.toString(),
            valueForegroundColor = if (snapshot.evictedKeys == 0L) context.theme.value else context.theme.warning,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 16,
            text = footerText(),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawLeftMetric(
        context: TuiContext,
        row: Int,
        label: String,
        value: String,
        valueForegroundColor: TextColor = context.theme.value,
    ) {
        MetricRow.draw(
            context = context,
            row = row,
            label = label,
            value = compactMetricValue(value),
            labelColumn = 7,
            valueColumn = 20,
            labelWidth = 11,
            valueForegroundColor = valueForegroundColor,
        )
    }

    private fun drawRightMetric(
        context: TuiContext,
        row: Int,
        label: String,
        value: String,
        valueForegroundColor: TextColor = context.theme.value,
    ) {
        MetricRow.draw(
            context = context,
            row = row,
            label = label,
            value = compactMetricValue(value),
            labelColumn = 42,
            valueColumn = 55,
            labelWidth = 11,
            valueForegroundColor = valueForegroundColor,
        )
    }

    private fun compactMetricValue(value: String): String {
        return if (value.length <= METRIC_VALUE_WIDTH) {
            value
        } else {
            "${value.take(METRIC_VALUE_WIDTH - 1)}~"
        }
    }

    private fun footerText(): String {
        return if (disconnect == null) {
            "k: key browser   q/ESC: exit"
        } else {
            "k: key browser   d: disconnect   q/ESC: exit"
        }
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

    private companion object {
        const val METRIC_VALUE_WIDTH = 20
    }
}
