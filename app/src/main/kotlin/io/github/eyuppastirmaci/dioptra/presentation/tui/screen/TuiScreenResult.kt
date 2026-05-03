package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

sealed interface TuiScreenResult {

    /**
     * Keeps the current screen active.
     */
    data object Continue : TuiScreenResult

    /**
     * Exits the TUI application.
     */
    data object Exit : TuiScreenResult

    /**
     * Replaces the current screen with another screen.
     */
    data class Navigate(
        val nextScreen: TuiScreen,
    ) : TuiScreenResult
}