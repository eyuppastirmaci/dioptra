package io.github.eyuppastirmaci.dioptra.application.risk

import io.github.eyuppastirmaci.dioptra.config.RiskAnalysisSettings
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyType
import io.github.eyuppastirmaci.dioptra.domain.risk.MemoryPolicyWarning
import io.github.eyuppastirmaci.dioptra.domain.risk.RedisMemoryPolicySnapshot
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskKeyFinding
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskLevel
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskPatternFinding
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskReason
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisRawRiskKeyMetadata
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisRiskScanRequest
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisRiskScanSource
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisMemoryUsageMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTtlMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTypeMapper
import io.github.eyuppastirmaci.dioptra.application.namespace.NamespaceResolver
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class LoadRiskAnalysisUseCase(
    private val engine: RiskAnalysisEngine,
) {

    suspend fun load(): RiskAnalysisSnapshot {
        return engine.analyze()
    }
}

class RiskAnalysisEngine(
    private val redisRiskScanSource: RedisRiskScanSource,
    private val redisTypeMapper: RedisTypeMapper,
    private val redisTtlMapper: RedisTtlMapper,
    private val redisMemoryUsageMapper: RedisMemoryUsageMapper,
    private val namespaceResolver: NamespaceResolver,
    private val riskAnalysisSettings: RiskAnalysisSettings,
) {

    suspend fun analyze(): RiskAnalysisSnapshot {
        val allFindings = mutableListOf<RiskKeyFinding>()
        val patternAccumulators = linkedMapOf<String, RiskPatternAccumulator>()
        var cursor = INITIAL_CURSOR
        var finished = false

        while (!finished) {
            currentCoroutineContext().ensureActive()

            val page = redisRiskScanSource.scanPage(
                RedisRiskScanRequest(
                    cursor = cursor,
                    count = riskAnalysisSettings.normalizedScanCount,
                )
            )

            for (rawKey in page.keys) {
                currentCoroutineContext().ensureActive()

                val finding = mapFinding(rawKey)
                allFindings += finding

                val patternName = namespaceResolver.resolve(rawKey.name).displayName
                patternAccumulators.getOrPut(patternName) { RiskPatternAccumulator(patternName) }
                    .add(finding)
            }

            cursor = page.nextCursor
            finished = page.finished
        }

        val topKeyCount = riskAnalysisSettings.normalizedTopKeyCount
        val noTtlKeyCount = allFindings.count { RiskReason.NO_TTL in it.riskReasons }.toLong()
        val memoryPolicy = redisRiskScanSource.fetchMemoryPolicy()
        val riskyPatterns = patternAccumulators.values
            .map { it.toFinding() }
            .filter { it.noTtlKeyCount > 0L || it.bigKeyCount > 0L || it.largeCollectionKeyCount > 0L }
            .sortedWith(
                compareByDescending<RiskPatternFinding> { it.riskLevel.ordinal }
                    .thenByDescending { it.estimatedMemoryBytes }
                    .thenByDescending { it.noTtlKeyCount }
                    .thenBy { it.patternName }
            )
            .take(topKeyCount)

        return RiskAnalysisSnapshot(
            analyzedKeyCount = allFindings.size.toLong(),
            noTtlKeyCount = noTtlKeyCount,
            bigKeyCount = allFindings.count { RiskReason.BIG_KEY in it.riskReasons }.toLong(),
            largeHashCount = allFindings.count { RiskReason.LARGE_HASH in it.riskReasons }.toLong(),
            largeListCount = allFindings.count { RiskReason.LARGE_LIST in it.riskReasons }.toLong(),
            largeSetCount = allFindings.count { RiskReason.LARGE_SET in it.riskReasons }.toLong(),
            largeZsetCount = allFindings.count { RiskReason.LARGE_ZSET in it.riskReasons }.toLong(),
            largeStreamCount = allFindings.count { RiskReason.LARGE_STREAM in it.riskReasons }.toLong(),
            topLargestKeys = allFindings
                .filter { it.memoryBytes != null }
                .sortedWith(compareByDescending<RiskKeyFinding> { it.memoryBytes }.thenBy { it.name })
                .take(topKeyCount),
            topNoTtlKeys = allFindings
                .filter { RiskReason.NO_TTL in it.riskReasons }
                .sortedWith(compareByDescending<RiskKeyFinding> { it.memoryBytes ?: -1L }.thenBy { it.name })
                .take(topKeyCount),
            riskyPatterns = riskyPatterns,
            warnings = buildMemoryPolicyWarnings(
                memoryPolicy = memoryPolicy,
                analyzedKeyCount = allFindings.size.toLong(),
                noTtlKeyCount = noTtlKeyCount,
            ),
        )
    }

    private fun mapFinding(rawKey: RedisRawRiskKeyMetadata): RiskKeyFinding {
        val type = redisTypeMapper.map(rawKey.type)
        val ttl = redisTtlMapper.map(rawKey.ttlSeconds)
        val memoryUsage = redisMemoryUsageMapper.map(rawKey.memoryUsageBytes)
        val reasons = buildList {
            if (ttl == RedisKeyTtlStatus.NoExpiration) {
                add(RiskReason.NO_TTL)
            }
            if (memoryUsage is RedisKeyMemoryUsage.Known &&
                memoryUsage.bytes >= riskAnalysisSettings.normalizedBigKeyThresholdBytes
            ) {
                add(RiskReason.BIG_KEY)
            }
            collectionRiskReason(type, rawKey.collectionSize)?.let(::add)
        }

        return RiskKeyFinding(
            name = rawKey.name,
            type = type,
            ttl = ttl,
            memoryUsage = memoryUsage,
            collectionSize = rawKey.collectionSize,
            riskReasons = reasons,
        )
    }

    private fun collectionRiskReason(
        type: RedisKeyType,
        collectionSize: Long?,
    ): RiskReason? {
        val size = collectionSize ?: return null
        return when {
            type == RedisKeyType.HASH && size >= riskAnalysisSettings.normalizedLargeHashFieldThreshold -> RiskReason.LARGE_HASH
            type == RedisKeyType.LIST && size >= riskAnalysisSettings.normalizedLargeListLengthThreshold -> RiskReason.LARGE_LIST
            type == RedisKeyType.SET && size >= riskAnalysisSettings.normalizedLargeSetMemberThreshold -> RiskReason.LARGE_SET
            type == RedisKeyType.ZSET && size >= riskAnalysisSettings.normalizedLargeZsetMemberThreshold -> RiskReason.LARGE_ZSET
            type == RedisKeyType.STREAM && size >= riskAnalysisSettings.normalizedLargeStreamEntryThreshold -> RiskReason.LARGE_STREAM
            else -> null
        }
    }

    private fun buildMemoryPolicyWarnings(
        memoryPolicy: RedisMemoryPolicySnapshot,
        analyzedKeyCount: Long,
        noTtlKeyCount: Long,
    ): List<MemoryPolicyWarning> {
        val warnings = mutableListOf<MemoryPolicyWarning>()
        val maxmemory = memoryPolicy.maxmemoryBytes
        val usedMemory = memoryPolicy.usedMemoryBytes
        val policy = memoryPolicy.maxmemoryPolicy.orEmpty().lowercase()

        if (maxmemory == 0L) {
            warnings += MemoryPolicyWarning(
                level = RiskLevel.INFO,
                message = "No maxmemory limit is configured.",
            )
        }

        if (maxmemory != null && maxmemory > 0L) {
            if (policy == "noeviction") {
                warnings += MemoryPolicyWarning(
                    level = RiskLevel.CRITICAL,
                    message = "Maxmemory uses noeviction; writes can fail when memory is exhausted.",
                )
            }

            if (usedMemory != null && usedMemory.toDouble() / maxmemory.toDouble() >= HIGH_MEMORY_PRESSURE_RATIO) {
                warnings += MemoryPolicyWarning(
                    level = RiskLevel.RISKY,
                    message = "Used memory is at least 80% of maxmemory.",
                )
            }
        }

        if ((memoryPolicy.evictedKeys ?: 0L) > 0L) {
            warnings += MemoryPolicyWarning(
                level = RiskLevel.WATCH,
                message = "Redis reports ${memoryPolicy.evictedKeys} evicted keys.",
            )
        }

        if (policy.startsWith("volatile-") && analyzedKeyCount > 0L) {
            val noTtlRatio = noTtlKeyCount.toDouble() / analyzedKeyCount.toDouble()
            if (noTtlRatio >= HIGH_NO_TTL_RATIO) {
                warnings += MemoryPolicyWarning(
                    level = RiskLevel.RISKY,
                    message = "Volatile eviction policy ignores many No-TTL keys.",
                )
            }
        }

        return warnings
    }

    private val RiskKeyFinding.memoryBytes: Long?
        get() = when (val usage = memoryUsage) {
            RedisKeyMemoryUsage.Unknown -> null
            is RedisKeyMemoryUsage.Known -> usage.bytes
        }

    private companion object {
        const val INITIAL_CURSOR = "0"
        const val HIGH_MEMORY_PRESSURE_RATIO = 0.80
        const val HIGH_NO_TTL_RATIO = 0.50
    }
}

private class RiskPatternAccumulator(
    private val patternName: String,
) {

    private var keyCount = 0L
    private var noTtlKeyCount = 0L
    private var bigKeyCount = 0L
    private var largeCollectionKeyCount = 0L
    private var estimatedMemoryBytes = 0L

    fun add(finding: RiskKeyFinding) {
        keyCount += 1
        if (RiskReason.NO_TTL in finding.riskReasons) noTtlKeyCount += 1
        if (RiskReason.BIG_KEY in finding.riskReasons) bigKeyCount += 1
        if (finding.riskReasons.any { it.isLargeCollection }) largeCollectionKeyCount += 1
        val memoryUsage = finding.memoryUsage
        if (memoryUsage is RedisKeyMemoryUsage.Known) {
            estimatedMemoryBytes += memoryUsage.bytes
        }
    }

    fun toFinding(): RiskPatternFinding {
        val noTtlRatio = if (keyCount == 0L) 0.0 else noTtlKeyCount.toDouble() / keyCount.toDouble()
        val riskLevel = when {
            noTtlRatio >= 0.80 && (bigKeyCount > 0L || largeCollectionKeyCount > 0L) -> RiskLevel.CRITICAL
            noTtlRatio >= 0.50 || bigKeyCount > 0L || largeCollectionKeyCount > 0L -> RiskLevel.RISKY
            noTtlKeyCount > 0L -> RiskLevel.WATCH
            else -> RiskLevel.INFO
        }

        return RiskPatternFinding(
            patternName = patternName,
            keyCount = keyCount,
            noTtlKeyCount = noTtlKeyCount,
            bigKeyCount = bigKeyCount,
            largeCollectionKeyCount = largeCollectionKeyCount,
            estimatedMemoryBytes = estimatedMemoryBytes,
            riskLevel = riskLevel,
        )
    }

    private val RiskReason.isLargeCollection: Boolean
        get() = this == RiskReason.LARGE_HASH ||
            this == RiskReason.LARGE_LIST ||
            this == RiskReason.LARGE_SET ||
            this == RiskReason.LARGE_ZSET ||
            this == RiskReason.LARGE_STREAM
}
