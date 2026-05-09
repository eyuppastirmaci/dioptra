package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.profile

import io.github.eyuppastirmaci.dioptra.application.profile.ProfileImportExportSummary

data class ProfileImportExportState(
    val summary: ProfileImportExportSummary,
    val message: String? = null,
    val error: String? = null,
)
