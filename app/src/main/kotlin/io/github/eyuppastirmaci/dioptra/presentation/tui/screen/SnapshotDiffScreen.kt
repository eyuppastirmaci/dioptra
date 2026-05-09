package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.snapshot.DiffAnalysisSnapshotsUseCase
import io.github.eyuppastirmaci.dioptra.domain.snapshot.diff.AnalysisSnapshotDiff
import io.github.eyuppastirmaci.dioptra.domain.snapshot.diff.MetricDelta
import io.github.eyuppastirmaci.dioptra.domain.snapshot.diff.NamespaceDelta
import io.github.eyuppastirmaci.dioptra.infrastructure.snapshot.AnalysisSnapshotFile
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.snapshot.SnapshotDiffMode
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.snapshot.SnapshotDiffState
import java.nio.file.Path

class SnapshotDiffScreen(
    private val diffAnalysisSnapshotsUseCase: DiffAnalysisSnapshotsUseCase,
    private val mode: SnapshotDiffMode = SnapshotDiffMode.GENERAL,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private var state: SnapshotDiffState = SnapshotDiffState.Loading

    init {
        loadSnapshots()
    }

    override fun render(context: TuiContext) {
        context.clearBackground()
        drawPanel(context)
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isExitKey(keyStroke) -> TuiScreenResult.Exit
            isBackKey(keyStroke) -> {
                val currentState = state
                if (currentState is SnapshotDiffState.Result) {
                    state = SnapshotDiffState.Selecting(
                        snapshots = diffAnalysisSnapshotsUseCase.listSnapshots(),
                    )
                    TuiScreenResult.Continue
                } else {
                    TuiScreenResult.Navigate(back())
                }
            }
            isCharacter(keyStroke, 'r') -> {
                loadSnapshots()
                TuiScreenResult.Continue
            }
            keyStroke.keyType == KeyType.Enter -> {
                runDiff()
                TuiScreenResult.Continue
            }
            keyStroke.keyType == KeyType.Character && keyStroke.character == ' ' -> {
                toggleSelectedSnapshot()
                TuiScreenResult.Continue
            }
            keyStroke.keyType == KeyType.ArrowDown || isCharacter(keyStroke, 'j') -> {
                move(delta = 1)
                TuiScreenResult.Continue
            }
            keyStroke.keyType == KeyType.ArrowUp || isCharacter(keyStroke, 'k') -> {
                move(delta = -1)
                TuiScreenResult.Continue
            }
            else -> TuiScreenResult.Continue
        }
    }

    private fun loadSnapshots() {
        state = runCatching {
            val snapshots = diffAnalysisSnapshotsUseCase.listSnapshots()
            if (snapshots.size < 2) {
                SnapshotDiffState.Error("At least two saved snapshots are required.")
            } else {
                SnapshotDiffState.Selecting(snapshots = snapshots)
            }
        }.getOrElse { exception ->
            SnapshotDiffState.Error(UserFacingErrorMessage.from(exception))
        }
    }

    private fun runDiff() {
        val selecting = state as? SnapshotDiffState.Selecting ?: return
        if (selecting.selectedPaths.size != 2) {
            state = selecting.copy(message = "Select exactly two snapshots with Space.")
            return
        }

        val orderedPaths = selecting.selectedPaths.orderedBySnapshotList(selecting.snapshots)
        state = runCatching {
            SnapshotDiffState.Result(
                diff = diffAnalysisSnapshotsUseCase.diff(
                    baselinePath = orderedPaths[1],
                    comparisonPath = orderedPaths[0],
                )
            )
        }.getOrElse { exception ->
            selecting.copy(message = UserFacingErrorMessage.from(exception))
        }
    }

    private fun toggleSelectedSnapshot() {
        val selecting = state as? SnapshotDiffState.Selecting ?: return
        val snapshot = selecting.snapshots.getOrNull(selecting.selectedIndex) ?: return
        val nextSelected = if (snapshot.path in selecting.selectedPaths) {
            selecting.selectedPaths - snapshot.path
        } else {
            (listOf(snapshot.path) + selecting.selectedPaths).take(2)
        }
        state = selecting.copy(
            selectedPaths = nextSelected,
            message = null,
        )
    }

    private fun move(delta: Int) {
        when (val currentState = state) {
            is SnapshotDiffState.Selecting -> {
                val nextIndex = (currentState.selectedIndex + delta).coerceIn(0, currentState.snapshots.lastIndex)
                state = currentState.copy(
                    selectedIndex = nextIndex,
                    scrollOffset = adjustScroll(nextIndex, currentState.scrollOffset, SELECT_VISIBLE_ROWS),
                )
            }
            is SnapshotDiffState.Result -> {
                state = currentState.copy(
                    scrollOffset = (currentState.scrollOffset + delta).coerceAtLeast(0),
                )
            }
            else -> Unit
        }
    }

    private fun drawPanel(context: TuiContext) {
        val panel = TuiRect(left = 2, top = 1, width = 76, height = 23)
        Panel.draw(context = context, rect = panel)
        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = screenTitle(),
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        when (val currentState = state) {
            SnapshotDiffState.Loading -> drawLoading(context, panel)
            is SnapshotDiffState.Selecting -> drawSelecting(context, panel, currentState)
            is SnapshotDiffState.Result -> drawResult(context, panel, currentState.diff, currentState.scrollOffset)
            is SnapshotDiffState.Error -> drawError(context, panel, currentState.message)
        }

        drawFooter(context, panel)
    }

    private fun drawLoading(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 5,
            text = "Loading saved snapshots...",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawSelecting(
        context: TuiContext,
        panel: TuiRect,
        state: SnapshotDiffState.Selecting,
    ) {
        val subtitle = when (mode) {
            SnapshotDiffMode.GENERAL -> "${state.snapshots.size} snapshots found. Select two; newest two are preselected."
            SnapshotDiffMode.CLEANUP -> "${state.snapshots.size} snapshots found. Pick before + after cleanup snapshots."
        }
        context.putText(
            column = panel.left + 3,
            row = panel.top + 3,
            text = fit(subtitle, panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
        state.message?.let {
            context.putText(
                column = panel.left + 3,
                row = panel.top + 4,
                text = fit(it, panel.width - 6),
                foregroundColor = context.theme.warning,
                backgroundColor = context.theme.panel,
            )
        }

        state.snapshots.drop(state.scrollOffset).take(SELECT_VISIBLE_ROWS).forEachIndexed { relativeIndex, snapshot ->
            val absoluteIndex = state.scrollOffset + relativeIndex
            val row = panel.top + FIRST_SELECT_ROW + relativeIndex
            val isSelectedRow = absoluteIndex == state.selectedIndex
            val marker = snapshotMarker(snapshot, state)
            drawSnapshotRow(context, panel, row, snapshot, isSelectedRow, marker)
        }
    }

    private fun drawSnapshotRow(
        context: TuiContext,
        panel: TuiRect,
        row: Int,
        snapshot: AnalysisSnapshotFile,
        isSelectedRow: Boolean,
        marker: String,
    ) {
        val bg = if (isSelectedRow) context.theme.border else context.theme.panel
        val fg = if (isSelectedRow) context.theme.panel else context.theme.value
        val text = "$marker ${snapshot.generatedAt}  ${snapshot.profileName} db:${snapshot.selectedDatabase}"

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
            text = fit(text, panel.width - 6),
            foregroundColor = fg,
            backgroundColor = bg,
            bold = isSelectedRow,
        )
    }

    private fun snapshotMarker(
        snapshot: AnalysisSnapshotFile,
        state: SnapshotDiffState.Selecting,
    ): String {
        if (snapshot.path !in state.selectedPaths) {
            return "[ ]"
        }
        if (mode == SnapshotDiffMode.GENERAL || state.selectedPaths.size != 2) {
            return "[x]"
        }

        val orderedPaths = state.selectedPaths.orderedBySnapshotList(state.snapshots)
        return if (snapshot.path == orderedPaths.first()) {
            "[after]"
        } else {
            "[before]"
        }
    }

    private fun drawResult(
        context: TuiContext,
        panel: TuiRect,
        diff: AnalysisSnapshotDiff,
        scrollOffset: Int,
    ) {
        val rows = resultRows(diff)
        context.putText(
            column = panel.left + 3,
            row = panel.top + 3,
            text = fit(resultSubtitle(diff), panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
        rows.drop(scrollOffset).take(RESULT_VISIBLE_ROWS).forEachIndexed { index, rowText ->
            context.putText(
                column = panel.left + 3,
                row = panel.top + FIRST_RESULT_ROW + index,
                text = fit(rowText, panel.width - 6),
                foregroundColor = context.theme.value,
                backgroundColor = context.theme.panel,
            )
        }
    }

    private fun resultRows(diff: AnalysisSnapshotDiff): List<String> {
        return when (mode) {
            SnapshotDiffMode.GENERAL -> generalResultRows(diff)
            SnapshotDiffMode.CLEANUP -> cleanupResultRows(diff)
        }
    }

    private fun generalResultRows(diff: AnalysisSnapshotDiff): List<String> = buildList {
        if (!diff.hasChanges) {
            add("No differences found.")
            return@buildList
        }

        add("Metrics")
        diff.metricDeltas.filter { it.changed }.take(10).forEach { metric ->
            add(formatMetric(metric))
        }

        add("")
        add("Namespaces")
        add("+ added: ${summarize(diff.addedNamespaces)}")
        add("- removed: ${summarize(diff.removedNamespaces)}")
        diff.changedNamespaces.take(8).forEach { namespace ->
            add(formatNamespaceDelta(namespace))
        }

        add("")
        add("Risk keys")
        add("+ added: ${summarize(diff.addedRiskKeys)}")
        add("- removed: ${summarize(diff.removedRiskKeys)}")

        add("")
        add("Warnings")
        add("+ added: ${summarize(diff.addedWarnings)}")
        add("- removed: ${summarize(diff.removedWarnings)}")
    }

    private fun cleanupResultRows(diff: AnalysisSnapshotDiff): List<String> = buildList {
        if (!diff.hasChanges) {
            add("No cleanup impact detected between the selected snapshots.")
            return@buildList
        }

        add("Cleanup impact")
        add(cleanupMetric(diff, "Total keys"))
        add(cleanupMetric(diff, "No-TTL keys"))
        add(cleanupMetric(diff, "Big keys"))
        add(cleanupMetric(diff, "Large collections"))
        add(cleanupMetric(diff, "Evicted keys"))

        add("")
        add("Namespace cleanup")
        add("Removed namespaces: ${summarize(diff.removedNamespaces)}")
        add("Added namespaces: ${summarize(diff.addedNamespaces)}")
        diff.changedNamespaces.take(8).forEach { namespace ->
            add(formatNamespaceDelta(namespace))
        }

        add("")
        add("Risk cleanup")
        add("Resolved risky keys: ${summarize(diff.removedRiskKeys)}")
        add("New risky keys: ${summarize(diff.addedRiskKeys)}")

        add("")
        add("Warnings")
        add("Resolved warnings: ${summarize(diff.removedWarnings)}")
        add("New warnings: ${summarize(diff.addedWarnings)}")
    }

    private fun cleanupMetric(
        diff: AnalysisSnapshotDiff,
        label: String,
    ): String {
        val metric = diff.metricDeltas.firstOrNull { it.label == label }
            ?: return "- $label: n/a"
        return "- $label: ${metric.before} -> ${metric.after} (${metric.delta})"
    }

    private fun formatMetric(metric: MetricDelta): String {
        return "- ${metric.label}: ${metric.before} -> ${metric.after} (${metric.delta})"
    }

    private fun formatNamespaceDelta(namespace: NamespaceDelta): String {
        val memory = diffAnalysisSnapshotsUseCase.formatMemoryDelta(namespace.memoryBytesDelta)
        return "- ${namespace.namespaceName}: keys ${signed(namespace.keyCountDelta)}, noTTL ${signed(namespace.noTtlKeyCountDelta)}, mem $memory, score ${signed(namespace.healthScoreDelta.toLong())}"
    }

    private fun drawError(
        context: TuiContext,
        panel: TuiRect,
        message: String,
    ) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 5,
            text = fit(message, panel.width - 6),
            foregroundColor = context.theme.warning,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawFooter(context: TuiContext, panel: TuiRect) {
        val footer = when (state) {
            is SnapshotDiffState.Result -> "j/k:scroll  b/esc:select  r:reload  q:exit"
            else -> selectionFooter()
        }
        context.putText(
            column = panel.left + 3,
            row = panel.top + panel.height - 2,
            text = fit(footer, panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun screenTitle(): String {
        return when (mode) {
            SnapshotDiffMode.GENERAL -> "Diff Analysis Snapshots"
            SnapshotDiffMode.CLEANUP -> "Compare Before/After Cleanup"
        }
    }

    private fun resultSubtitle(diff: AnalysisSnapshotDiff): String {
        return when (mode) {
            SnapshotDiffMode.GENERAL -> "${diff.baselineGeneratedAt} -> ${diff.comparisonGeneratedAt}"
            SnapshotDiffMode.CLEANUP -> "Before ${diff.baselineGeneratedAt} -> After ${diff.comparisonGeneratedAt}"
        }
    }

    private fun selectionFooter(): String {
        return when (mode) {
            SnapshotDiffMode.GENERAL -> "space:select  enter:diff  j/k:move  r:reload  b/esc:back  q:exit"
            SnapshotDiffMode.CLEANUP -> "space:mark before/after  enter:compare  j/k:move  r:reload  esc:back"
        }
    }

    private fun List<Path>.orderedBySnapshotList(snapshots: List<AnalysisSnapshotFile>): List<Path> {
        val order = snapshots.mapIndexed { index, snapshot -> snapshot.path to index }.toMap()
        return sortedBy { order[it] ?: Int.MAX_VALUE }
    }

    private fun summarize(values: List<String>): String {
        if (values.isEmpty()) {
            return "none"
        }
        return values.take(3).joinToString(separator = ", ", truncated = "...")
    }

    private fun signed(value: Long): String {
        return if (value > 0L) "+$value" else value.toString()
    }

    private fun adjustScroll(
        selectedIndex: Int,
        currentScroll: Int,
        visibleRows: Int,
    ): Int {
        return when {
            selectedIndex < currentScroll -> selectedIndex
            selectedIndex >= currentScroll + visibleRows -> selectedIndex - visibleRows + 1
            else -> currentScroll
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

    private companion object {
        const val FIRST_SELECT_ROW = 6
        const val SELECT_VISIBLE_ROWS = 12
        const val FIRST_RESULT_ROW = 5
        const val RESULT_VISIBLE_ROWS = 15
    }
}
