package io.github.eyuppastirmaci.dioptra.presentation.tui.terminal

import com.googlecode.lanterna.terminal.Terminal

fun interface TerminalFactory {

    fun create(): Terminal
}