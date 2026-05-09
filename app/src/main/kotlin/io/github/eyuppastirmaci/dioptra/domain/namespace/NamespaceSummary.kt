package io.github.eyuppastirmaci.dioptra.domain.namespace

data class NamespaceSummary(
    val identity: NamespaceIdentity,
    val keyCount: Long,
    val averageTtlSeconds: Long?,
    val noTtlKeyCount: Long,
    val estimatedMemoryUsage: NamespaceMemoryUsage,
    val ttlCoverage: NamespaceTtlCoverage,
    val memoryConcentrationPercent: Double,
    val health: NamespaceHealth,
    val riskReasons: List<NamespaceRiskReason>,
    val namingAnomalies: List<NamespaceNamingAnomaly>,
    val unexpectedNamespaceSignal: UnexpectedNamespaceSignal?,
)