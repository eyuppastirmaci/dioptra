package io.github.eyuppastirmaci.dioptra.presentation.tui

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.TerminalScreen
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.TuiScreen
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.TuiScreenResult
import io.github.eyuppastirmaci.dioptra.presentation.tui.terminal.TerminalFactory
import io.github.eyuppastirmaci.dioptra.presentation.tui.theme.DioptraTheme

class TuiApplication(
    private val terminalFactory: TerminalFactory,
    private val theme: DioptraTheme = DioptraTheme.dark(),
) {

    /**
     * Starts the terminal UI lifecycle and renders the given initial screen.
     */
    fun run(initialScreen: TuiScreen) {
        val terminal = terminalFactory.create()
        val screen = TerminalScreen(terminal)

        var activeScreen = initialScreen

        try {
            screen.startScreen()
            screen.clear()

            eventLoop@ while (true) {
                val keyStroke = screen.pollInput()

                if (keyStroke != null) {
                    if (shouldExitGlobally(keyStroke)) {
                        break@eventLoop
                    }

                    when (val result = activeScreen.handleInput(keyStroke)) {
                        TuiScreenResult.Continue -> Unit

                        TuiScreenResult.Exit -> {
                            break@eventLoop
                        }

                        is TuiScreenResult.Navigate -> {
                            activeScreen.close()
                            activeScreen = result.nextScreen
                        }
                    }
                }

                when (val result = activeScreen.tick()) {
                    TuiScreenResult.Continue -> Unit

                    TuiScreenResult.Exit -> {
                        break@eventLoop
                    }

                    is TuiScreenResult.Navigate -> {
                        activeScreen.close()
                        activeScreen = result.nextScreen
                    }
                }

                renderScreen(
                    terminalScreen = screen,
                    activeScreen = activeScreen,
                )

                Thread.sleep(EVENT_LOOP_DELAY_MILLIS)
            }
        } finally {
            activeScreen.close()
            screen.stopScreen()
            terminal.close()
        }
    }

    private fun renderScreen(
        terminalScreen: TerminalScreen,
        activeScreen: TuiScreen,
    ) {
        val context = TuiContext(
            screen = terminalScreen,
            graphics = terminalScreen.newTextGraphics(),
            theme = theme,
        )

        context.clearBackground()
        activeScreen.render(context)
        terminalScreen.refresh()
    }

    private fun shouldExitGlobally(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.EOF
    }

    private companion object {
        const val EVENT_LOOP_DELAY_MILLIS = 80L
    }
}