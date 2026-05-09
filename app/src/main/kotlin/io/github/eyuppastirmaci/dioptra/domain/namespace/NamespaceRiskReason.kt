package io.github.eyuppastirmaci.dioptra.domain.namespace

enum class NamespaceRiskReason {
    HIGH_NO_TTL_RATIO,
    HIGH_MEMORY_CONCENTRATION,
    LARGE_NAMESPACE,
    LOW_TTL_COVERAGE,
    UNEXPECTED_NAMESPACE,
    NAMING_ANOMALY,
    UNKNOWN_MEMORY_USAGE,
}