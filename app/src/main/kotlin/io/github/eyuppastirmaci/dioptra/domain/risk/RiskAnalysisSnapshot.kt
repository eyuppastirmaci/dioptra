package io.github.eyuppastirmaci.dioptra.domain.risk

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyType

data class RiskAnalysisSnapshot(
    val analyzedKeyCount: Long,
    val noTtlKeyCount: Long,
    val bigKeyCount: Long,
    val largeHashCount: Long,
    val largeListCount: Long,
    val largeSetCount: Long,
    val largeZsetCount: Long,
    val largeStreamCount: Long,
    val topLargestKeys: List<RiskKeyFinding>,
    val topNoTtlKeys: List<RiskKeyFinding>,
    val riskyPatterns: List<RiskPatternFinding>,
    val warnings: List<MemoryPolicyWarning>,
) {

    val largeCollectionKeyCount: Long
        get() = largeHashCount + largeListCount + largeSetCount + largeZsetCount + largeStreamCount
}

data class RiskKeyFinding(
    val name: String,
    val type: RedisKeyType,
    val ttl: RedisKeyTtlStatus,
    val memoryUsage: RedisKeyMemoryUsage,
    val collectionSize: Long?,
    val riskReasons: List<RiskReason>,
)

data class RiskPatternFinding(
    val patternName: String,
    val keyCount: Long,
    val noTtlKeyCount: Long,
    val bigKeyCount: Long,
    val largeCollectionKeyCount: Long,
    val estimatedMemoryBytes: Long,
    val riskLevel: RiskLevel,
)

data class MemoryPolicyWarning(
    val level: RiskLevel,
    val message: String,
)

data class RedisMemoryPolicySnapshot(
    val usedMemoryBytes: Long?,
    val maxmemoryBytes: Long?,
    val maxmemoryPolicy: String?,
    val evictedKeys: Long?,
)

enum class RiskReason {
    NO_TTL,
    BIG_KEY,
    LARGE_HASH,
    LARGE_LIST,
    LARGE_SET,
    LARGE_ZSET,
    LARGE_STREAM,
}

enum class RiskLevel {
    INFO,
    WATCH,
    RISKY,
    CRITICAL,
}
