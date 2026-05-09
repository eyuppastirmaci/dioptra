package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.commandpalette

import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.TuiScreenResult

data class CommandPaletteItem(
    val title: String,
    val description: String,
    val shortcut: String? = null,
    val keywords: List<String> = emptyList(),
    val action: () -> TuiScreenResult,
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) {
            return true
        }

        val normalizedQuery = query.trim().lowercase()
        return searchableText.contains(normalizedQuery)
    }

    private val searchableText: String
        get() = buildString {
            append(title)
            append(' ')
            append(description)
            append(' ')
            shortcut?.let(::append)
            append(' ')
            append(keywords.joinToString(separator = " "))
        }.lowercase()
}
