package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeySummary
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeySortMode
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keyoperation.KeyOperationToast
import io.github.eyuppastirmaci.dioptra.presentation.tui.ttl.LiveTtlDisplay

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
    val productionSafety: Boolean,
    val operationToast: KeyOperationToast?,
    val liveTtlDisplay: (RedisKeySummary) -> LiveTtlDisplay,
)
