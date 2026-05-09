package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.profile

import io.github.eyuppastirmaci.dioptra.application.profile.TeamProfileTemplateSummary

data class TeamProfileTemplateState(
    val summary: TeamProfileTemplateSummary,
    val message: String? = null,
    val error: String? = null,
)
