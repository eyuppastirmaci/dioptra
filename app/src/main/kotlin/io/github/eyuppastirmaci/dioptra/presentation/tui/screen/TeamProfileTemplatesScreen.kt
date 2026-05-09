package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.profile.TeamProfileTemplateUseCase
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.profile.TeamProfileTemplateState

class TeamProfileTemplatesScreen(
    private val useCase: TeamProfileTemplateUseCase,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private var state = TeamProfileTemplateState(summary = useCase.summary())

    override fun render(context: TuiContext) {
        context.clearBackground()
        drawPanel(context)
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isExitKey(keyStroke) -> TuiScreenResult.Exit
            isBackKey(keyStroke) -> TuiScreenResult.Navigate(back())
            isCharacter(keyStroke, 'e') -> {
                exportTemplate()
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, 'i') -> {
                importTemplate()
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, 'r') -> {
                refresh()
                TuiScreenResult.Continue
            }
            else -> TuiScreenResult.Continue
        }
    }

    private fun exportTemplate() {
        state = runCatching {
            val result = useCase.exportActiveTemplate()
            TeamProfileTemplateState(
                summary = useCase.summary(),
                message = "Exported template '${result.profileName}' to ${result.path}.",
            )
        }.getOrElse { exception ->
            state.copy(
                summary = useCase.summary(),
                message = null,
                error = UserFacingErrorMessage.from(exception),
            )
        }
    }

    private fun importTemplate() {
        state = runCatching {
            val result = useCase.importTemplate()
            TeamProfileTemplateState(
                summary = useCase.summary(),
                message = "Imported ${result.addedCount} new, replaced ${result.replacedCount}; total ${result.totalProfiles}.",
            )
        }.getOrElse { exception ->
            state.copy(
                summary = useCase.summary(),
                message = null,
                error = UserFacingErrorMessage.from(exception),
            )
        }
    }

    private fun refresh() {
        state = TeamProfileTemplateState(
            summary = useCase.summary(),
            message = "Template status refreshed.",
        )
    }

    private fun drawPanel(context: TuiContext) {
        val panel = TuiRect(left = 2, top = 1, width = 88, height = 21)
        Panel.draw(context = context, rect = panel)

        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Team Profile Templates",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = panel.left + 3,
            row = panel.top + 3,
            text = "Share safe connection defaults and analysis settings with teammates.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        drawState(context, panel)
        drawFooter(context, panel)
    }

    private fun drawState(context: TuiContext, panel: TuiRect) {
        val summary = state.summary
        drawLine(context, panel, 5, "Active profile: ${summary.activeProfileName}")
        drawLine(context, panel, 6, "Saved profiles: ${summary.savedProfileCount}")
        drawLine(context, panel, 8, "Template export directory:")
        drawLine(context, panel, 9, summary.templateDirectory.toString(), value = true)
        drawLine(context, panel, 11, "Template import file:")
        drawLine(context, panel, 12, summary.templateImportPath.toString(), value = true)
        drawLine(
            context = context,
            panel = panel,
            relativeRow = 14,
            text = "Export uses the active connection without persisting the password.",
        )
        drawLine(
            context = context,
            panel = panel,
            relativeRow = 15,
            text = "Copy a shared template to the import file path, then press i.",
        )

        state.message?.let { message ->
            context.putText(
                column = panel.left + 3,
                row = panel.top + 17,
                text = fit(message, panel.width - 6),
                foregroundColor = context.theme.success,
                backgroundColor = context.theme.panel,
                bold = true,
            )
        }
        state.error?.let { error ->
            context.putText(
                column = panel.left + 3,
                row = panel.top + 17,
                text = fit(error, panel.width - 6),
                foregroundColor = context.theme.warning,
                backgroundColor = context.theme.panel,
                bold = true,
            )
        }
    }

    private fun drawLine(
        context: TuiContext,
        panel: TuiRect,
        relativeRow: Int,
        text: String,
        value: Boolean = false,
    ) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + relativeRow,
            text = fit(text, panel.width - 6),
            foregroundColor = if (value) context.theme.value else context.theme.label,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawFooter(context: TuiContext, panel: TuiRect) {
        context.putText(
            column = panel.left + 3,
            row = panel.top + panel.height - 2,
            text = fit("e:export active  i:import shared  r:refresh  b/esc:back  q:exit", panel.width - 6),
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
}
