package io.github.eyuppastirmaci.dioptra.domain.namespace

data class NamespaceTtlCoverage(
    val expiringKeyCount: Long,
    val noTtlKeyCount: Long,
    val unknownTtlKeyCount: Long,
    val coveragePercent: Double,
)