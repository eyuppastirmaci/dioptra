package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.namespace.LoadNamespaceDetailRequest
import io.github.eyuppastirmaci.dioptra.application.namespace.LoadNamespaceDetailUseCase
import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceDetailSnapshot
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceHealthLevel
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceMemoryUsage
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.ByteSizeFormatter
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.namespace.NamespaceDetailState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class NamespaceDetailScreen(
    private val namespaceName: String,
    private val loadNamespaceDetailUseCase: LoadNamespaceDetailUseCase,
    private val namespaceAnalysisSettings: NamespaceAnalysisSettings,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private val logger = LoggerFactory.getLogger(NamespaceDetailScreen::class.java)
    private val byteSizeFormatter = ByteSizeFormatter()
    private val screenScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            DioptraCoroutineExceptionHandler.create(
                logger = logger,
                contextName = "NamespaceDetailScreen",
                onError = { exception ->
                    state = NamespaceDetailState.Error(UserFacingErrorMessage.from(exception))
                },
            ),
    )

    private var loadJob: Job? = null

    @Volatile
    private var state: NamespaceDetailState = NamespaceDetailState.Loading

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
                state = NamespaceDetailState.Loading
                load()
                TuiScreenResult.Continue
            }
            else -> TuiScreenResult.Continue
        }
    }

    override fun close() {
        screenScope.cancel()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = screenScope.launch(Dispatchers.IO) {
            val snapshot = loadNamespaceDetailUseCase.load(
                LoadNamespaceDetailRequest(namespaceName = namespaceName),
            )

            state = if (snapshot == null) {
                NamespaceDetailState.Error("Namespace not found.")
            } else {
                NamespaceDetailState.Loaded(snapshot)
            }
        }
    }

    private fun drawPanel(context: TuiContext) {
        val panel = TuiRect(left = 2, top = 1, width = 76, height = 22)
        Panel.draw(context = context, rect = panel)

        drawHeader(context, panel)

        when (val currentState = state) {
            NamespaceDetailState.Loading -> drawLoading(context, panel)
            is NamespaceDetailState.Error -> drawError(context, panel, currentState.message)
            is NamespaceDetailState.Loaded -> drawSnapshot(context, panel, currentState.snapshot)
        }

        drawFooter(context, panel)
    }

    private fun drawHeader(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Namespace Detail",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 2,
            text = fit(namespaceName, panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 3,
            text = fit(filterEffectSummary(), panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawLoading(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 7,
            text = "Loading namespace detail...",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawError(context: TuiContext, panel: TuiRect, message: String) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 7,
            text = fit("Error: $message", panel.width - 6),
            foregroundColor = context.theme.danger,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawSnapshot(
        context: TuiContext,
        panel: TuiRect,
        snapshot: NamespaceDetailSnapshot,
    ) {
        val summary = snapshot.summary
        val avgTtl = summary.averageTtlSeconds?.let(::formatTtl) ?: "n/a"
        val memoryText = formatMemory(summary.estimatedMemoryUsage)

        context.putText(panel.left + 3, panel.top + 4, fit("Keys: ${summary.keyCount}   Avg TTL: $avgTtl", panel.width - 6), context.theme.value, context.theme.panel)
        context.putText(panel.left + 3, panel.top + 5, fit("TTL coverage: ${formatPercent(summary.ttlCoverage.coveragePercent)}   No TTL: ${summary.noTtlKeyCount}", panel.width - 6), context.theme.value, context.theme.panel)
        context.putText(panel.left + 3, panel.top + 6, fit("Memory: $memoryText   Concentration: ${formatPercent(summary.memoryConcentrationPercent)}", panel.width - 6), context.theme.value, context.theme.panel)
        context.putText(panel.left + 3, panel.top + 7, fit("Health: ${summary.health.level.name.lowercase()} (${summary.health.score})", panel.width - 6), healthColor(context, summary.health.level), context.theme.panel)

        context.putText(panel.left + 3, panel.top + 9, "TTL Buckets", context.theme.label, context.theme.panel, bold = true)
        snapshot.ttlBuckets.take(4).forEachIndexed { index, bucket ->
            context.putText(
                column = panel.left + 3,
                row = panel.top + 10 + index,
                text = fit("${bucket.label}: ${bucket.keyCount}", 32),
                foregroundColor = context.theme.value,
                backgroundColor = context.theme.panel,
            )
        }

        context.putText(panel.left + 38, panel.top + 9, "Patterns", context.theme.label, context.theme.panel, bold = true)
        snapshot.dominantPatterns.take(4).forEachIndexed { index, pattern ->
            context.putText(
                column = panel.left + 38,
                row = panel.top + 10 + index,
                text = fit(pattern, 35),
                foregroundColor = context.theme.value,
                backgroundColor = context.theme.panel,
            )
        }

        context.putText(panel.left + 3, panel.top + 15, "No-TTL Samples", context.theme.label, context.theme.panel, bold = true)
        drawList(
            context = context,
            startColumn = panel.left + 3,
            startRow = panel.top + 16,
            width = 32,
            items = snapshot.sampleKeysWithoutTtl,
        )

        context.putText(panel.left + 38, panel.top + 15, "Anomaly Samples", context.theme.label, context.theme.panel, bold = true)
        drawList(
            context = context,
            startColumn = panel.left + 38,
            startRow = panel.top + 16,
            width = 35,
            items = snapshot.sampleAnomalousKeys,
        )

        val note = snapshot.notes.firstOrNull()
            ?: summary.unexpectedNamespaceSignal?.reason
            ?: summary.health.primaryReason
        context.putText(
            column = panel.left + 3,
            row = panel.top + 20,
            text = fit("Note: ${filterNotePrefix()}$note", panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun filterEffectSummary(): String {
        val allowCount = namespaceAnalysisSettings.normalizedAllowedKeyPatterns.size
        val ignoreCount = namespaceAnalysisSettings.normalizedIgnoredKeyPatterns.size
        if (allowCount == 0 && ignoreCount == 0) {
            return "No allow/ignore filters active for this analysis."
        }

        return "Filters: allow=$allowCount suppress alerts, ignore=$ignoreCount excludes matching keys from overview totals."
    }

    private fun filterNotePrefix(): String {
        val allowCount = namespaceAnalysisSettings.normalizedAllowedKeyPatterns.size
        val ignoreCount = namespaceAnalysisSettings.normalizedIgnoredKeyPatterns.size
        return if (allowCount == 0 && ignoreCount == 0) {
            ""
        } else {
            "Filters active. "
        }
    }

    private fun drawList(
        context: TuiContext,
        startColumn: Int,
        startRow: Int,
        width: Int,
        items: List<String>,
    ) {
        val values = if (items.isEmpty()) listOf("none") else items.take(3)
        values.forEachIndexed { index, item ->
            context.putText(
                column = startColumn,
                row = startRow + index,
                text = fit("- $item", width),
                foregroundColor = context.theme.value,
                backgroundColor = context.theme.panel,
            )
        }
    }

    private fun drawFooter(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + panel.height - 2,
            text = fit("r:refresh  b/esc:back  q:exit", panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun healthColor(context: TuiContext, level: NamespaceHealthLevel) = when (level) {
        NamespaceHealthLevel.HEALTHY -> context.theme.success
        NamespaceHealthLevel.WATCH -> context.theme.warning
        NamespaceHealthLevel.RISKY -> context.theme.danger
        NamespaceHealthLevel.CRITICAL -> context.theme.danger
    }

    private fun formatMemory(memoryUsage: NamespaceMemoryUsage): String {
        return when (memoryUsage) {
            NamespaceMemoryUsage.Unknown -> "unknown"
            is NamespaceMemoryUsage.Estimated -> byteSizeFormatter.format(memoryUsage.totalBytes)
        }
    }

    private fun formatPercent(value: Double): String {
        return "${value.toInt()}%"
    }

    private fun formatTtl(seconds: Long): String {
        return when {
            seconds < 60L -> "${seconds}s"
            seconds < 3_600L -> "${seconds / 60}m"
            seconds < 86_400L -> "${seconds / 3_600}h"
            else -> "${seconds / 86_400}d"
        }
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
}