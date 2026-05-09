package io.github.eyuppastirmaci.dioptra.application.snapshot

import io.github.eyuppastirmaci.dioptra.domain.snapshot.diff.AnalysisSnapshotDiff
import io.github.eyuppastirmaci.dioptra.infrastructure.snapshot.AnalysisSnapshotFile
import io.github.eyuppastirmaci.dioptra.infrastructure.snapshot.AnalysisSnapshotRepository
import java.nio.file.Path

class DiffAnalysisSnapshotsUseCase(
    private val repository: AnalysisSnapshotRepository,
    private val diffEngine: AnalysisSnapshotDiffEngine,
) {

    fun listSnapshots(): List<AnalysisSnapshotFile> {
        return repository.list()
    }

    fun diff(
        baselinePath: Path,
        comparisonPath: Path,
    ): AnalysisSnapshotDiff {
        val baseline = repository.load(baselinePath)
        val comparison = repository.load(comparisonPath)
        require(baseline.schemaVersion == comparison.schemaVersion) {
            "Snapshot schema versions do not match: ${baseline.schemaVersion} vs ${comparison.schemaVersion}."
        }
        return diffEngine.diff(
            baseline = baseline,
            comparison = comparison,
        )
    }

    fun formatMemoryDelta(delta: Long?): String {
        return diffEngine.formatMemoryDelta(delta)
    }
}
