package io.github.eyuppastirmaci.dioptra.presentation.tui.component

import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect

object Panel {

    fun draw(
        context: TuiContext,
        rect: TuiRect,
    ) {
        context.fillRect(
            rect = rect,
            backgroundColor = context.theme.panel,
        )

        val horizontalLine = "─".repeat(rect.width - 2)

        context.putText(
            column = rect.left,
            row = rect.top,
            text = "┌$horizontalLine┐",
            foregroundColor = context.theme.border,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = rect.left,
            row = rect.top + rect.height - 1,
            text = "└$horizontalLine┘",
            foregroundColor = context.theme.border,
            backgroundColor = context.theme.panel,
        )

        for (row in rect.top + 1 until rect.top + rect.height - 1) {
            context.putText(
                column = rect.left,
                row = row,
                text = "│",
                foregroundColor = context.theme.border,
                backgroundColor = context.theme.panel,
            )

            context.putText(
                column = rect.left + rect.width - 1,
                row = row,
                text = "│",
                foregroundColor = context.theme.border,
                backgroundColor = context.theme.panel,
            )
        }
    }
}