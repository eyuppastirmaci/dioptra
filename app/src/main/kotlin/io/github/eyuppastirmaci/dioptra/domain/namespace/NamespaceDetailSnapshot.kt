package io.github.eyuppastirmaci.dioptra.domain.namespace

data class NamespaceDetailSnapshot(
    val summary: NamespaceSummary,
    val ttlBuckets: List<NamespaceTtlBucket>,
    val sampleKeysWithoutTtl: List<String>,
    val sampleAnomalousKeys: List<String>,
    val dominantPatterns: List<String>,
    val notes: List<String>,
)