package io.github.eyuppastirmaci.dioptra.domain.namespace

data class NamespaceAnalysisSnapshot(
    val summaries: List<NamespaceSummary>,
    val topRiskyNamespaces: List<NamespaceSummary>,
    val unexpectedNamespaces: List<UnexpectedNamespaceSignal>,
    val anomalousNamespaces: List<NamespaceSummary>,
    val totalNamespaceCount: Int,
    val analyzedKeyCount: Long,
    val ignoredKeyCount: Long,
    val alertSuppressedKeyCount: Long,
    val analysisWarnings: List<String>,
)