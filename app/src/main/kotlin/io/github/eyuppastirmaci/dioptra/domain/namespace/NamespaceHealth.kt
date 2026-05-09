package io.github.eyuppastirmaci.dioptra.domain.namespace

data class NamespaceHealth(
    val score: Int,
    val level: NamespaceHealthLevel,
    val primaryReason: String,
)

enum class NamespaceHealthLevel {
    HEALTHY,
    WATCH,
    RISKY,
    CRITICAL,
}