package io.github.eyuppastirmaci.dioptra.presentation.tui.terminal

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal

class SwingTerminalFactory(
    private val initialTerminalSize: TerminalSize = TerminalSize(88, 24),
) : TerminalFactory {

    override fun create(): Terminal {
        return DefaultTerminalFactory()
            .setInitialTerminalSize(initialTerminalSize)
            .createTerminal()
    }
}