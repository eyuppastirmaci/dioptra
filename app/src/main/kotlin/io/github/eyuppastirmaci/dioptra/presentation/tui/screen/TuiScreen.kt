package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext

interface TuiScreen {

    /**
     * Draws the current screen content using the active TUI context.
     */
    fun render(context: TuiContext)

    /**
     * Handles keyboard input and returns the next screen action.
     */
    fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return TuiScreenResult.Continue
    }

    /**
     * Runs periodic screen work between keyboard events.
     */
    fun tick(): TuiScreenResult {
        return TuiScreenResult.Continue
    }

    /**
     * Releases screen-level resources when the screen is closed or replaced.
     */
    fun close() {
        // Default no-op.
    }
}