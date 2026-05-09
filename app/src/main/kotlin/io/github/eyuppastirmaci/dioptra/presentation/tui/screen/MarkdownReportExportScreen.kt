package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.report.ExportMarkdownReportUseCase
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.report.MarkdownReportExportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class MarkdownReportExportScreen(
    private val dashboardSnapshot: RedisDashboardSnapshot,
    private val exportMarkdownReportUseCase: ExportMarkdownReportUseCase,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private val logger = LoggerFactory.getLogger(MarkdownReportExportScreen::class.java)
    private val screenScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            DioptraCoroutineExceptionHandler.create(
                logger = logger,
                contextName = "MarkdownReportExportScreen",
                onError = { exception ->
                    state = MarkdownReportExportState.Error(UserFacingErrorMessage.from(exception))
                },
            ),
    )

    private var exportJob: Job? = null

    @Volatile
    private var state: MarkdownReportExportState = MarkdownReportExportState.Loading

    init {
        export()
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
                export()
                TuiScreenResult.Continue
            }
            else -> TuiScreenResult.Continue
        }
    }

    override fun close() {
        exportJob?.cancel()
        screenScope.cancel()
    }

    private fun export() {
        exportJob?.cancel()
        state = MarkdownReportExportState.Loading
        exportJob = screenScope.launch(Dispatchers.IO) {
            val result = exportMarkdownReportUseCase.export(dashboardSnapshot)
            state = MarkdownReportExportState.Exported(result.path)
        }
    }

    private fun drawPanel(context: TuiContext) {
        val panel = TuiRect(left = 2, top = 1, width = 76, height = 16)
        Panel.draw(context = context, rect = panel)

        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Markdown Report Export",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        when (val currentState = state) {
            MarkdownReportExportState.Loading -> drawLoading(context, panel)
            is MarkdownReportExportState.Exported -> drawExported(context, panel, currentState)
            is MarkdownReportExportState.Error -> drawError(context, panel, currentState.message)
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
            text = "Collecting deterministic snapshots...",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 6,
            text = "This may take a moment on large Redis keyspaces.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawExported(
        context: TuiContext,
        panel: TuiRect,
        state: MarkdownReportExportState.Exported,
    ) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + 4,
            text = "Report exported successfully.",
            foregroundColor = context.theme.success,
            backgroundColor = context.theme.panel,
            bold = true,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 6,
            text = "Path:",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panel.left + 3,
            row = panel.top + 7,
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
            text = "Report export failed.",
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
        return isCharacter(keyStroke, 'r') && state !is MarkdownReportExportState.Loading
    }

    private fun isCharacter(keyStroke: KeyStroke, expected: Char): Boolean {
        return keyStroke.keyType == KeyType.Character &&
            keyStroke.character?.lowercaseChar() == expected
    }
}
