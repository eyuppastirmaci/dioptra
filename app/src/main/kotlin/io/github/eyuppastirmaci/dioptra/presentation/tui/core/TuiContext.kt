package io.github.eyuppastirmaci.dioptra.presentation.tui.core

import com.googlecode.lanterna.SGR
import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.TextGraphics
import com.googlecode.lanterna.screen.TerminalScreen
import io.github.eyuppastirmaci.dioptra.presentation.tui.theme.DioptraTheme

class TuiContext(
    val screen: TerminalScreen,
    val graphics: TextGraphics,
    val theme: DioptraTheme,
) {

    fun clearBackground() {
        graphics.setBackgroundColor(theme.background)
        graphics.setForegroundColor(theme.value)
        graphics.fillRectangle(
            TerminalPosition(0, 0),
            screen.terminalSize,
            ' ',
        )
    }

    fun fillRect(
        rect: TuiRect,
        backgroundColor: TextColor,
        fillChar: Char = ' ',
    ) {
        graphics.setBackgroundColor(backgroundColor)
        graphics.fillRectangle(
            TerminalPosition(rect.left, rect.top),
            TerminalSize(rect.width, rect.height),
            fillChar,
        )
    }

    fun putText(
        column: Int,
        row: Int,
        text: String,
        foregroundColor: TextColor,
        backgroundColor: TextColor,
        bold: Boolean = false,
    ) {
        graphics.setForegroundColor(foregroundColor)
        graphics.setBackgroundColor(backgroundColor)

        if (bold) {
            graphics.putString(TerminalPosition(column, row), text, SGR.BOLD)
        } else {
            graphics.putString(TerminalPosition(column, row), text)
        }
    }
}