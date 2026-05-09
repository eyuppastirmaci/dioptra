package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.snapshot

import io.github.eyuppastirmaci.dioptra.domain.snapshot.diff.AnalysisSnapshotDiff
import io.github.eyuppastirmaci.dioptra.infrastructure.snapshot.AnalysisSnapshotFile
import java.nio.file.Path

sealed interface SnapshotDiffState {
    data object Loading : SnapshotDiffState

    data class Selecting(
        val snapshots: List<AnalysisSnapshotFile>,
        val selectedPaths: List<Path> = snapshots.take(2).map { it.path },
        val selectedIndex: Int = 0,
        val scrollOffset: Int = 0,
        val message: String? = null,
    ) : SnapshotDiffState

    data class Result(
        val diff: AnalysisSnapshotDiff,
        val scrollOffset: Int = 0,
    ) : SnapshotDiffState

    data class Error(
        val message: String,
    ) : SnapshotDiffState
}
