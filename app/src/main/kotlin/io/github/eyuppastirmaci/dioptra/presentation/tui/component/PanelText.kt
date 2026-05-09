package io.github.eyuppastirmaci.dioptra.presentation.tui.component

import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect

object PanelText {

    fun clearLine(
        context: TuiContext,
        panelRect: TuiRect,
        row: Int,
        horizontalPadding: Int = 3,
    ) {
        context.fillRect(
            rect = TuiRect(
                left = panelRect.left + horizontalPadding,
                top = row,
                width = panelRect.width - horizontalPadding * 2,
                height = 1,
            ),
            backgroundColor = context.theme.panel,
        )
    }
}
