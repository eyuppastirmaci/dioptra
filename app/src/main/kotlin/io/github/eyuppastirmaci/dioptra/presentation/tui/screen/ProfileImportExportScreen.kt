package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.profile.ProfileImportExportUseCase
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.profile.ProfileImportExportState

class ProfileImportExportScreen(
    private val useCase: ProfileImportExportUseCase,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private var state = ProfileImportExportState(summary = useCase.summary())

    override fun render(context: TuiContext) {
        context.clearBackground()
        drawPanel(context)
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isExitKey(keyStroke) -> TuiScreenResult.Exit
            isBackKey(keyStroke) -> TuiScreenResult.Navigate(back())
            isCharacter(keyStroke, 'e') -> {
                exportProfiles()
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, 'i') -> {
                importProfiles()
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, 'r') -> {
                refresh()
                TuiScreenResult.Continue
            }
            else -> TuiScreenResult.Continue
        }
    }

    private fun exportProfiles() {
        state = runCatching {
            val result = useCase.exportProfiles()
            ProfileImportExportState(
                summary = useCase.summary(),
                message = "Exported ${result.profileCount} profiles to ${result.path}.",
            )
        }.getOrElse { exception ->
            state.copy(
                summary = useCase.summary(),
                message = null,
                error = UserFacingErrorMessage.from(exception),
            )
        }
    }

    private fun importProfiles() {
        state = runCatching {
            val result = useCase.importProfiles()
            ProfileImportExportState(
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
        state = ProfileImportExportState(
            summary = useCase.summary(),
            message = "Profile status refreshed.",
        )
    }

    private fun drawPanel(context: TuiContext) {
        val panel = TuiRect(left = 2, top = 1, width = 86, height = 20)
        Panel.draw(context = context, rect = panel)

        context.putText(
            column = panel.left + 3,
            row = panel.top + 1,
            text = "Profile Import/Export",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = panel.left + 3,
            row = panel.top + 3,
            text = "Move saved connection profiles between Dioptra installs.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        drawState(context, panel)
        drawFooter(context, panel)
    }

    private fun drawState(context: TuiContext, panel: TuiRect) {
        val summary = state.summary
        drawLine(context, panel, 5, "Saved profiles: ${summary.profileCount}")
        drawLine(context, panel, 6, "Default profile: ${summary.defaultProfile ?: "-"}")
        drawLine(context, panel, 8, "Export directory:")
        drawLine(context, panel, 9, summary.exportDirectory.toString(), value = true)
        drawLine(context, panel, 11, "Import file:")
        drawLine(context, panel, 12, summary.importPath.toString(), value = true)
        drawLine(
            context = context,
            panel = panel,
            relativeRow = 14,
            text = "Copy a .conf export to the import file path, then press i.",
        )

        state.message?.let { message ->
            context.putText(
                column = panel.left + 3,
                row = panel.top + 16,
                text = fit(message, panel.width - 6),
                foregroundColor = context.theme.success,
                backgroundColor = context.theme.panel,
                bold = true,
            )
        }
        state.error?.let { error ->
            context.putText(
                column = panel.left + 3,
                row = panel.top + 16,
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
            text = fit("e:export  i:import  r:refresh  b/esc:back  q:exit", panel.width - 6),
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
