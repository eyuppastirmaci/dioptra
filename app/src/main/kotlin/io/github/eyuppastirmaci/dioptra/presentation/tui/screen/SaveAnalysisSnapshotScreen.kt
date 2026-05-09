package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.snapshot.SaveAnalysisSnapshotUseCase
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.snapshot.SaveAnalysisSnapshotState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class SaveAnalysisSnapshotScreen(
    private val dashboardSnapshot: RedisDashboardSnapshot,
    private val saveAnalysisSnapshotUseCase: SaveAnalysisSnapshotUseCase,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private val logger = LoggerFactory.getLogger(SaveAnalysisSnapshotScreen::class.java)
    private val screenScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            DioptraCoroutineExceptionHandler.create(
                logger = logger,
                contextName = "SaveAnalysisSnapshotScreen",
                onError = { exception ->
                    state = SaveAnalysisSnapshotState.Error(UserFacingErrorMessage.from(exception))
                },
            ),
    )

    private var saveJob: Job? = null

    @Volatile
    private var state: SaveAnalysisSnapshotState = SaveAnalysisSnapshotState.Loading

    init {
        save()
    }

    override fun render(context: TuiContext) {
        context.clearBackground()
        drawPanel(context)
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isExitKey(keyStroke) -> TuiScreenResult.Exit
            isBackKey(keyStroke) -> TuiScreenResult.Navigate(back())
            isRetryKey(keyStroke) -> {
                save()
                TuiScreenResult.Continue
            }
            else -> TuiScreenResult.Continue
        }
    }

    override fun close() {
        saveJob?.cancel()
        screenScope.cancel()
    }

    private fun save() {
        saveJob?.cancel()
        state = SaveAnalysisSnapshotState.Loading
        saveJob = screenScope.launch(Dispatchers.IO) {
            val result = saveAnalysisSnapshotUseCase.save(dashboardSnapshot)
            state = SaveAnalysisSnapshotState.Saved(
                path = result.path,
                schemaVersion = result.schemaVersion,
            )
        }
    }

    private fun drawPanel(context: TuiContext) {
        val panel = TuiRect(left = 2, top = 1, width = 76, height = 16)
        Panel.draw(context = context, rect = panel)

        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Save Analysis Snapshot",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        when (val currentState = state) {
            SaveAnalysisSnapshotState.Loading -> drawLoading(context, panel)
            is SaveAnalysisSnapshotState.Saved -> drawSaved(context, panel, currentState)
            is SaveAnalysisSnapshotState.Error -> drawError(context, panel, currentState.message)
        }

        context.putText(
            column = panel.left + 3,
            row = panel.top + panel.height - 2,
            text = fit("r:retry  b/esc:back  q:exit", panel.width - 6),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawLoading(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 4,
            text = "Collecting deterministic analysis snapshot...",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 6,
            text = "The JSON file will be written to ~/.dioptra/snapshots.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawSaved(
        context: TuiContext,
        panel: TuiRect,
        state: SaveAnalysisSnapshotState.Saved,
    ) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 4,
            text = "Snapshot saved successfully.",
            foregroundColor = context.theme.success,
            backgroundColor = context.theme.panel,
            bold = true,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 6,
            text = "Schema version: ${state.schemaVersion}",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 8,
            text = "Path:",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 9,
            text = fit(state.path.toString(), panel.width - 6),
            foregroundColor = context.theme.value,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawError(
        context: TuiContext,
        panel: TuiRect,
        message: String,
    ) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 4,
            text = "Snapshot save failed.",
            foregroundColor = context.theme.danger,
            backgroundColor = context.theme.panel,
            bold = true,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 6,
            text = fit(message, panel.width - 6),
            foregroundColor = context.theme.warning,
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

    private fun isRetryKey(keyStroke: KeyStroke): Boolean {
        return isCharacter(keyStroke, 'r') && state !is SaveAnalysisSnapshotState.Loading
    }

    private fun isCharacter(keyStroke: KeyStroke, expected: Char): Boolean {
        return keyStroke.keyType == KeyType.Character &&
            keyStroke.character?.lowercaseChar() == expected
    }
}
