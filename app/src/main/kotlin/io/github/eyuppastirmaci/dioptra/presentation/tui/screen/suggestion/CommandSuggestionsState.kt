package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.suggestion

import io.github.eyuppastirmaci.dioptra.domain.suggestion.CommandSuggestion

data class CommandSuggestionsState(
    val suggestions: List<CommandSuggestion>,
    val riskStatus: RiskStatus,
    val selectedIndex: Int = 0,
    val scrollOffset: Int = 0,
) {
    sealed interface RiskStatus {
        data object Loading : RiskStatus
        data object Loaded : RiskStatus
        data class Error(val message: String) : RiskStatus
    }

    val dashboardCount: Int
        get() = suggestions.count { it.category != io.github.eyuppastirmaci.dioptra.domain.suggestion.SuggestionCategory.RISK }

    val riskCount: Int
        get() = suggestions.count { it.category == io.github.eyuppastirmaci.dioptra.domain.suggestion.SuggestionCategory.RISK }

    val selectedSuggestion: CommandSuggestion?
        get() = suggestions.getOrNull(selectedIndex)
}
