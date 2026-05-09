package io.github.eyuppastirmaci.dioptra.domain.suggestion

enum class SuggestionCategory(val badge: String, val label: String) {
    MEMORY("MEM   ", "Memory"),
    CLIENTS("CLIENT", "Clients"),
    PERSISTENCE("PERSIS", "Persistence"),
    REPLICATION("REPLIC", "Replication"),
    PERFORMANCE("PERF  ", "Performance"),
    KEYSPACE("KEYSP ", "Keyspace"),
    RISK("RISK  ", "Risk"),
}

data class CommandSuggestion(
    val category: SuggestionCategory,
    val title: String,
    val reason: String? = null,
    val commands: List<String>,
)
