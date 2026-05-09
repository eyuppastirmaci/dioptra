package io.github.eyuppastirmaci.dioptra.domain.snapshot

data class AnalysisSnapshot(
    val schemaVersion: Int,
    val generatedAt: String,
    val profileName: String,
    val selectedDatabase: Int,
    val dashboard: DashboardSnapshot,
    val namespaceAnalysis: NamespaceAnalysisSnapshotData,
    val riskAnalysis: RiskAnalysisSnapshotData,
    val slowlog: SlowlogSnapshotData,
    val commandStats: List<CommandStatSnapshot>,
    val latencyStats: List<LatencyStatSnapshot>,
    val commandSuggestions: List<CommandSuggestionSnapshot>,
)

data class DashboardSnapshot(
    val status: String,
    val redisVersion: String,
    val uptime: String,
    val usedMemoryHuman: String,
    val memoryFragmentationHint: String,
    val memoryFragmentationHealthy: Boolean,
    val connectedClients: Int,
    val connectedClientsHealthy: Boolean,
    val blockedClients: Int,
    val instantaneousOpsPerSecond: Int,
    val totalCommandsProcessed: Long,
    val keyspaceHits: Long,
    val keyspaceMisses: Long,
    val totalKeys: Long,
    val maxmemoryPolicy: String,
    val evictedKeys: Long,
    val rdbStatus: String,
    val rdbStatusHealthy: Boolean,
    val aofEnabled: Boolean,
    val aofStatus: String,
    val aofStatusHealthy: Boolean,
    val replicationRole: String,
    val connectedReplicas: Int,
    val masterLinkStatus: String,
    val masterLinkHealthy: Boolean,
)

data class NamespaceAnalysisSnapshotData(
    val totalNamespaceCount: Int,
    val analyzedKeyCount: Long,
    val ignoredKeyCount: Long,
    val alertSuppressedKeyCount: Long,
    val warnings: List<String>,
    val namespaces: List<NamespaceSnapshot>,
    val topRiskyNamespaces: List<String>,
    val unexpectedNamespaces: List<UnexpectedNamespaceSnapshot>,
    val anomalousNamespaces: List<String>,
)

data class NamespaceSnapshot(
    val name: String,
    val normalizedName: String,
    val keyCount: Long,
    val averageTtlSeconds: Long?,
    val noTtlKeyCount: Long,
    val estimatedMemoryBytes: Long?,
    val ttlCoveragePercent: Double,
    val memoryConcentrationPercent: Double,
    val healthScore: Int,
    val healthLevel: String,
    val healthReason: String,
    val riskReasons: List<String>,
    val namingAnomalies: List<String>,
    val unexpectedReason: String?,
)

data class UnexpectedNamespaceSnapshot(
    val namespaceName: String,
    val reason: String,
)

data class RiskAnalysisSnapshotData(
    val analyzedKeyCount: Long,
    val noTtlKeyCount: Long,
    val bigKeyCount: Long,
    val largeCollectionKeyCount: Long,
    val topLargestKeys: List<RiskKeySnapshot>,
    val topNoTtlKeys: List<RiskKeySnapshot>,
    val riskyPatterns: List<RiskPatternSnapshot>,
    val warnings: List<RiskWarningSnapshot>,
)

data class RiskKeySnapshot(
    val name: String,
    val type: String,
    val ttlStatus: String,
    val ttlSeconds: Long?,
    val memoryBytes: Long?,
    val collectionSize: Long?,
    val riskReasons: List<String>,
)

data class RiskPatternSnapshot(
    val patternName: String,
    val keyCount: Long,
    val noTtlKeyCount: Long,
    val bigKeyCount: Long,
    val largeCollectionKeyCount: Long,
    val estimatedMemoryBytes: Long,
    val riskLevel: String,
)

data class RiskWarningSnapshot(
    val level: String,
    val message: String,
)

data class SlowlogSnapshotData(
    val totalEntries: Long,
    val entries: List<SlowlogEntrySnapshot>,
)

data class SlowlogEntrySnapshot(
    val id: Long,
    val timestampSeconds: Long,
    val durationMicroseconds: Long,
    val command: String,
    val arguments: List<String>,
    val clientAddress: String?,
    val clientName: String?,
)

data class CommandStatSnapshot(
    val command: String,
    val calls: Long,
    val totalUsec: Long,
    val usecPerCall: Double,
    val rejectedCalls: Long,
    val failedCalls: Long,
)

data class LatencyStatSnapshot(
    val command: String,
    val p50Usec: Double,
    val p99Usec: Double,
    val p999Usec: Double,
)

data class CommandSuggestionSnapshot(
    val category: String,
    val title: String,
    val reason: String?,
    val commands: List<String>,
)
