package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyBrowserPage

sealed interface KeyBrowserState {

    data class Loading(
        val cursor: String,
    ) : KeyBrowserState

    data class Loaded(
        val page: RedisKeyBrowserPage,
    ) : KeyBrowserState

    data class Error(
        val message: String,
    ) : KeyBrowserState

    data class Cancelled(
        val cursor: String,
    ) : KeyBrowserState
}
