package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.namespace

import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceDetailSnapshot

sealed interface NamespaceDetailState {

    data object Loading : NamespaceDetailState

    data class Loaded(
        val snapshot: NamespaceDetailSnapshot,
    ) : NamespaceDetailState

    data class Error(
        val message: String,
    ) : NamespaceDetailState
}