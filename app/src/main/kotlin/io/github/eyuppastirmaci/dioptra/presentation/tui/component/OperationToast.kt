package io.github.eyuppastirmaci.dioptra.presentation.tui.component

import com.googlecode.lanterna.TextColor
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keyoperation.KeyOperationMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keyoperation.KeyOperationToast

object OperationToast {

    fun draw(
        context: TuiContext,
        containerRect: TuiRect,
        toast: KeyOperationToast?,
    ) {
        if (toast == null) {
            return
        }

        val lines = listOf(toast.message.text) + toast.details.take(MAX_DETAIL_LINES)
        val contentWidth = lines
            .maxOf { it.length }
            .coerceIn(MIN_CONTENT_WIDTH, MAX_CONTENT_WIDTH)
            .coerceAtMost(containerRect.width - OUTER_PADDING * 2 - INNER_PADDING * 2)
        val rect = TuiRect(
            left = containerRect.left + containerRect.width - contentWidth - INNER_PADDING * 2 - OUTER_PADDING,
            top = containerRect.top + containerRect.height - lines.size - INNER_PADDING * 2 - FOOTER_CLEARANCE,
            width = contentWidth + INNER_PADDING * 2,
            height = lines.size + INNER_PADDING * 2,
        )

        Panel.draw(context, rect)

        lines.forEachIndexed { index, line ->
            context.putText(
                column = rect.left + INNER_PADDING,
                row = rect.top + INNER_PADDING + index,
                text = line.truncate(contentWidth).padEnd(contentWidth),
                foregroundColor = foregroundColor(context, toast.message, index),
                backgroundColor = context.theme.panel,
                bold = index == 0,
            )
        }
    }

    private fun foregroundColor(
        context: TuiContext,
        message: KeyOperationMessage,
        lineIndex: Int,
    ): TextColor {
        if (lineIndex > 0) {
            return context.theme.hint
        }

        return when (message) {
            is KeyOperationMessage.Failure -> context.theme.warning
            is KeyOperationMessage.Info -> context.theme.hint
            is KeyOperationMessage.Success -> context.theme.success
        }
    }

    private fun String.truncate(maxLength: Int): String {
        return if (length <= maxLength) {
            this
        } else {
            take(maxLength - 1) + "~"
        }
    }

    private const val MIN_CONTENT_WIDTH = 22
    private const val MAX_CONTENT_WIDTH = 42
    private const val MAX_DETAIL_LINES = 3
    private const val INNER_PADDING = 2
    private const val OUTER_PADDING = 2
    private const val FOOTER_CLEARANCE = 4
}
