package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.report

import java.nio.file.Path

sealed interface MarkdownReportExportState {
    data object Loading : MarkdownReportExportState

    data class Exported(
        val path: Path,
    ) : MarkdownReportExportState

    data class Error(
        val message: String,
    ) : MarkdownReportExportState
}
