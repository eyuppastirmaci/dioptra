package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser

import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeySortMode

data class KeyBrowserRenderState(
    val state: KeyBrowserState,
    val pattern: String,
    val patternInput: String,
    val inputMode: KeyBrowserInputMode,
    val selectedKeyIndex: Int,
    val sortMode: RedisKeySortMode,
    val count: Long,
    val isLoading: Boolean,
    val canReturnBack: Boolean,
)
