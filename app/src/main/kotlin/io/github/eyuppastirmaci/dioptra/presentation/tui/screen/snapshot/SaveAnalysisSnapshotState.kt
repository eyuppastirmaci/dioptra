package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.snapshot

import java.nio.file.Path

sealed interface SaveAnalysisSnapshotState {
    data object Loading : SaveAnalysisSnapshotState

    data class Saved(
        val path: Path,
        val schemaVersion: Int,
    ) : SaveAnalysisSnapshotState

    data class Error(
        val message: String,
    ) : SaveAnalysisSnapshotState
}
