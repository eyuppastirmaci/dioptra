package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysUseCase
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyUseCase
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyValueUseCase
import io.github.eyuppastirmaci.dioptra.application.key.ExpireKeyUseCase
import io.github.eyuppastirmaci.dioptra.application.key.LoadKeyDetailUseCase
import io.github.eyuppastirmaci.dioptra.application.safety.OperationAuditLogger
import io.github.eyuppastirmaci.dioptra.application.safety.ProtectedNamespaceRules
import io.github.eyuppastirmaci.dioptra.application.commandstats.LoadCommandStatsUseCase
import io.github.eyuppastirmaci.dioptra.application.latency.LoadLatencyStatsUseCase
import io.github.eyuppastirmaci.dioptra.application.slowlog.LoadSlowlogUseCase
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
    private val expireKeyUseCase: ExpireKeyUseCase,
    private val deleteKeyUseCase: DeleteKeyUseCase,
    private val deleteKeyValueUseCase: DeleteKeyValueUseCase,
    private val loadSlowlogUseCase: LoadSlowlogUseCase,
    private val loadCommandStatsUseCase: LoadCommandStatsUseCase,
    private val loadLatencyStatsUseCase: LoadLatencyStatsUseCase,
    private val readOnly: Boolean,
    private val productionSafety: Boolean,
    private val protectedNamespaceRules: ProtectedNamespaceRules,
    private val operationAuditLogger: OperationAuditLogger,
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
                        expireKeyUseCase = expireKeyUseCase,
                        deleteKeyUseCase = deleteKeyUseCase,
                        deleteKeyValueUseCase = deleteKeyValueUseCase,
                        readOnly = readOnly,
                        productionSafety = productionSafety,
                        protectedNamespaceRules = protectedNamespaceRules,
                        operationAuditLogger = operationAuditLogger,
                        renderer = keyBrowserRenderer,
                        sorter = keyBrowserSorter,
                        keyMatcher = keyMatcher,
                        back = { this },
                    )
                )
            }

            isCharacter(keyStroke, 's') -> {
                TuiScreenResult.Navigate(
                    nextScreen = SlowlogScreen(
                        loadSlowlogUseCase = loadSlowlogUseCase,
                        back = { this },
                    )
                )
            }

            isCharacter(keyStroke, 'c') -> {
                TuiScreenResult.Navigate(
                    nextScreen = CommandStatsScreen(
                        loadCommandStatsUseCase = loadCommandStatsUseCase,
                        back = { this },
                    )
                )
            }

            isCharacter(keyStroke, 'l') -> {
                TuiScreenResult.Navigate(
                    nextScreen = LatencyStatsScreen(
                        loadLatencyStatsUseCase = loadLatencyStatsUseCase,
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
            height = 21,
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

        // Row 13 — persistence separator
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 13,
            text = "─── Persistence " + "─".repeat(50),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        // Row 14 — RDB status (left) + AOF status (right)
        val rdbLabel = if (snapshot.rdbBgsaveInProgress) "RDB saving…" else "RDB"
        drawLeftMetric(
            context = context,
            row = panelRect.top + 14,
            label = rdbLabel,
            value = snapshot.rdbStatus,
            valueForegroundColor = if (snapshot.rdbStatusHealthy) context.theme.value else context.theme.danger,
        )
        val aofValue = snapshot.aofStatus
        val aofColor = when {
            !snapshot.aofEnabled -> context.theme.hint
            snapshot.aofStatusHealthy -> context.theme.value
            else -> context.theme.danger
        }
        drawRightMetric(
            context = context,
            row = panelRect.top + 14,
            label = "AOF",
            value = aofValue,
            valueForegroundColor = aofColor,
        )

        // Row 15 — last save age (left) + unsaved changes (right)
        drawLeftMetric(
            context = context,
            row = panelRect.top + 15,
            label = "Last Save",
            value = snapshot.rdbLastSaveAge,
        )
        drawRightMetric(
            context = context,
            row = panelRect.top + 15,
            label = "Unsaved",
            value = snapshot.rdbChangesSinceLastSave.toString(),
            valueForegroundColor = if (snapshot.rdbChangesSinceLastSave == 0L) context.theme.value else context.theme.warning,
        )

        // Row 16 — replication separator
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 16,
            text = "─── Replication " + "─".repeat(50),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        // Row 17 — role (left) + replica count or link status (right)
        drawLeftMetric(
            context = context,
            row = panelRect.top + 17,
            label = "Role",
            value = snapshot.replicationRole,
        )
        if (snapshot.replicationRole == "slave") {
            val linkColor = if (snapshot.masterLinkHealthy) context.theme.value else context.theme.danger
            drawRightMetric(
                context = context,
                row = panelRect.top + 17,
                label = "Link",
                value = snapshot.masterLinkStatus,
                valueForegroundColor = linkColor,
            )
        } else {
            drawRightMetric(
                context = context,
                row = panelRect.top + 17,
                label = "Replicas",
                value = snapshot.connectedReplicas.toString(),
            )
        }

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + panelRect.height - 2,
            text = fitToPanelWidth(panelRect, footerText()),
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

    private fun fitToPanelWidth(panel: TuiRect, text: String): String {
        val maxWidth = (panel.width - 6).coerceAtLeast(0)
        return text.take(maxWidth)
    }

    private fun footerText(): String {
        val mode = buildString {
            if (readOnly) append("  ro")
            if (productionSafety) append("  safe")
        }
        val protected = if (protectedNamespaceRules.count > 0) {
            "  prot:${protectedNamespaceRules.count}"
        } else {
            ""
        }
        return if (disconnect == null) {
            "k:key  s:slow  c:cmd  l:lat  q/esc:exit$mode$protected"
        } else {
            "k:key  s:slow  c:cmd  l:lat  d:disc  q/esc:exit$mode$protected"
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
