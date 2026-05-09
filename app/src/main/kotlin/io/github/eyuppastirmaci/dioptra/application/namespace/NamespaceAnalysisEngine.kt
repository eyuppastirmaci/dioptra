package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceDetailSnapshot
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceNamingAnomaly
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceRiskReason
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceSummary
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceTtlBucket
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceTtlCoverage
import io.github.eyuppastirmaci.dioptra.domain.namespace.UnexpectedNamespaceSignal
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyScanClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyScanRequest
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisRawKeyMetadata
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisMemoryUsageMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTtlMapper
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class NamespaceAnalysisEngine(
    private val redisKeyBrowserClient: RedisKeyScanClient,
    private val redisTtlMapper: RedisTtlMapper,
    private val redisMemoryUsageMapper: RedisMemoryUsageMapper,
    private val namespaceAnalysisSettings: NamespaceAnalysisSettings,
    private val namespaceResolver: NamespaceResolver,
    private val namespaceHealthScorer: NamespaceHealthScorer,
) {

    private val keyPatternPolicy = NamespaceKeyPatternPolicy(namespaceAnalysisSettings)

    suspend fun analyze(
        request: LoadNamespaceAnalysisRequest = LoadNamespaceAnalysisRequest(),
    ): NamespaceAnalysisComputation {
        val accumulators = linkedMapOf<String, NamespaceAccumulator>()
        var cursor = request.cursor
        var finished = false
        var analyzedKeyCount = 0L
        var ignoredKeyCount = 0L
        var alertSuppressedKeyCount = 0L

        while (!finished) {
            currentCoroutineContext().ensureActive()

            val page = redisKeyBrowserClient.scanPage(
                RedisKeyScanRequest(
                    cursor = cursor,
                    pattern = request.pattern,
                    count = request.count,
                )
            )

            for (rawKey in page.keys) {
                currentCoroutineContext().ensureActive()

                if (keyPatternPolicy.isIgnored(rawKey.name)) {
                    ignoredKeyCount += 1
                    continue
                }

                val identity = namespaceResolver.resolve(rawKey.name)
                val suppressNamespaceAlerts = keyPatternPolicy.isAllowed(rawKey.name)
                if (suppressNamespaceAlerts) {
                    alertSuppressedKeyCount += 1
                }
                val accumulator = accumulators.getOrPut(identity.normalizedName) {
                    NamespaceAccumulator(
                        identity = identity,
                        namespaceAnalysisSettings = namespaceAnalysisSettings,
                        namespaceResolver = namespaceResolver,
                    )
                }

                accumulator.add(
                    rawKey = rawKey,
                    ttlStatus = redisTtlMapper.map(rawKey.ttlSeconds),
                    memoryUsage = redisMemoryUsageMapper.map(rawKey.memoryUsageBytes),
                    unexpected = !suppressNamespaceAlerts && namespaceResolver.isUnexpected(identity),
                    suppressNamingAlerts = suppressNamespaceAlerts,
                )
                analyzedKeyCount += 1
            }

            cursor = page.nextCursor
            finished = page.finished
        }

        val totalKnownMemoryBytes = accumulators.values.sumOf { it.knownMemoryBytes }
        val summaries = accumulators.values
            .map { it.toSummary(totalKnownMemoryBytes, namespaceHealthScorer) }
            .sortedWith(
                compareBy<NamespaceSummary> { it.health.score }
                    .thenByDescending { it.memoryConcentrationPercent }
                    .thenByDescending { it.keyCount }
                    .thenBy { it.identity.displayName }
            )

        val detailByNamespace = accumulators.values.associate { accumulator ->
            accumulator.identity.normalizedName to accumulator.toDetail(
                totalKnownMemoryBytes = totalKnownMemoryBytes,
                namespaceHealthScorer = namespaceHealthScorer,
            )
        }

        val analysisWarnings = buildList {
            if (summaries.any { NamespaceRiskReason.UNKNOWN_MEMORY_USAGE in it.riskReasons }) {
                add("Some keys do not expose MEMORY USAGE; memory totals are estimated.")
            }
            if (namespaceResolver.hasExpectations && summaries.any { it.unexpectedNamespaceSignal != null }) {
                add("Unexpected namespaces were discovered outside configured namespace rules.")
            }
        }

        val snapshot = NamespaceAnalysisSnapshot(
            summaries = summaries,
            topRiskyNamespaces = summaries.take(request.topRiskyNamespaceCount),
            unexpectedNamespaces = summaries.mapNotNull { it.unexpectedNamespaceSignal },
            anomalousNamespaces = summaries.filter { it.namingAnomalies.isNotEmpty() },
            totalNamespaceCount = summaries.size,
            analyzedKeyCount = analyzedKeyCount,
            ignoredKeyCount = ignoredKeyCount,
            alertSuppressedKeyCount = alertSuppressedKeyCount,
            analysisWarnings = analysisWarnings,
        )

        return NamespaceAnalysisComputation(
            snapshot = snapshot,
            detailsByNamespace = detailByNamespace,
        )
    }
}

data class NamespaceAnalysisComputation(
    val snapshot: NamespaceAnalysisSnapshot,
    val detailsByNamespace: Map<String, NamespaceDetailSnapshot>,
)

data class LoadNamespaceAnalysisRequest(
    val cursor: String = INITIAL_CURSOR,
    val pattern: String = DEFAULT_PATTERN,
    val count: Long = DEFAULT_SCAN_COUNT,
    val topRiskyNamespaceCount: Int = DEFAULT_TOP_RISKY_NAMESPACE_COUNT,
)

private class NamespaceAccumulator(
    val identity: io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceIdentity,
    private val namespaceAnalysisSettings: NamespaceAnalysisSettings,
    private val namespaceResolver: NamespaceResolver,
) {

    var keyCount: Long = 0
        private set

    var noTtlKeyCount: Long = 0
        private set

    var unknownTtlKeyCount: Long = 0
        private set

    var expiringKeyCount: Long = 0
        private set

    var totalExpiringTtlSeconds: Long = 0
        private set

    var knownMemoryBytes: Long = 0
        private set

    var knownMemoryKeyCount: Long = 0
        private set

    private var unexpectedReason: String? = null
    private var namingAnomalyCount: Int = 0
    private val namingAnomalies = mutableListOf<NamespaceNamingAnomaly>()
    private val sampleKeysWithoutTtl = linkedSetOf<String>()
    private val sampleAnomalousKeys = linkedSetOf<String>()
    private val childPatternCounts = linkedMapOf<String, Long>()
    private val ttlBucketCounts = linkedMapOf(
        TTL_BUCKET_NO_TTL to 0L,
        TTL_BUCKET_LT_1H to 0L,
        TTL_BUCKET_LT_1D to 0L,
        TTL_BUCKET_LT_7D to 0L,
        TTL_BUCKET_GTE_7D to 0L,
        TTL_BUCKET_UNKNOWN to 0L,
    )

    fun add(
        rawKey: RedisRawKeyMetadata,
        ttlStatus: RedisKeyTtlStatus,
        memoryUsage: RedisKeyMemoryUsage,
        unexpected: Boolean,
        suppressNamingAlerts: Boolean,
    ) {
        keyCount += 1
        recordTtl(rawKey.name, ttlStatus)
        recordMemory(memoryUsage)
        if (!suppressNamingAlerts) {
            recordNamingAnomaly(rawKey.name)
        }
        recordChildPattern(rawKey.name)

        if (unexpected && unexpectedReason == null) {
            unexpectedReason = "Namespace did not match any configured namespace rule"
        }
    }

    fun toSummary(
        totalKnownMemoryBytes: Long,
        namespaceHealthScorer: NamespaceHealthScorer,
    ): NamespaceSummary {
        val ttlCoverage = ttlCoverage()
        val memoryUsage = memoryUsage()
        val healthAssessment = namespaceHealthScorer.assess(
            NamespaceHealthInput(
                keyCount = keyCount,
                noTtlKeyCount = noTtlKeyCount,
                ttlCoveragePercent = ttlCoverage.coveragePercent,
                memoryConcentrationPercent = memoryConcentrationPercent(totalKnownMemoryBytes),
                unexpectedNamespace = unexpectedReason != null,
                anomalyCount = namingAnomalyCount,
                memoryUsageKnown = knownMemoryKeyCount == keyCount,
            )
        )

        return NamespaceSummary(
            identity = identity,
            keyCount = keyCount,
            averageTtlSeconds = averageTtlSeconds(),
            noTtlKeyCount = noTtlKeyCount,
            estimatedMemoryUsage = memoryUsage,
            ttlCoverage = ttlCoverage,
            memoryConcentrationPercent = memoryConcentrationPercent(totalKnownMemoryBytes),
            health = healthAssessment.health,
            riskReasons = healthAssessment.riskReasons,
            namingAnomalies = namingAnomalies.toList(),
            unexpectedNamespaceSignal = unexpectedSignal(),
        )
    }

    fun toDetail(
        totalKnownMemoryBytes: Long,
        namespaceHealthScorer: NamespaceHealthScorer,
    ): NamespaceDetailSnapshot {
        return NamespaceDetailSnapshot(
            summary = toSummary(totalKnownMemoryBytes, namespaceHealthScorer),
            ttlBuckets = ttlBucketCounts.map { (label, count) ->
                NamespaceTtlBucket(label = label, keyCount = count)
            },
            sampleKeysWithoutTtl = sampleKeysWithoutTtl.toList(),
            sampleAnomalousKeys = sampleAnomalousKeys.toList(),
            dominantPatterns = childPatternCounts.entries
                .sortedByDescending { it.value }
                .take(MAX_DETAIL_PATTERN_COUNT)
                .map { (pattern, count) -> "$pattern ($count keys)" },
            notes = buildList {
                if (unexpectedReason != null) {
                    add(unexpectedReason!!)
                }
                if (knownMemoryKeyCount in 1 until keyCount) {
                    add("Memory totals are partial: $knownMemoryKeyCount of $keyCount keys reported MEMORY USAGE.")
                }
                if (namingAnomalyCount > namingAnomalies.size) {
                    add("Only the first ${namingAnomalies.size} naming anomalies are sampled in this view.")
                }
            },
        )
    }

    private fun recordTtl(
        keyName: String,
        ttlStatus: RedisKeyTtlStatus,
    ) {
        when (ttlStatus) {
            RedisKeyTtlStatus.KeyDoesNotExist -> {
                unknownTtlKeyCount += 1
                incrementBucket(TTL_BUCKET_UNKNOWN)
            }
            RedisKeyTtlStatus.NoExpiration -> {
                noTtlKeyCount += 1
                incrementBucket(TTL_BUCKET_NO_TTL)
                if (sampleKeysWithoutTtl.size < MAX_SAMPLE_KEY_COUNT) {
                    sampleKeysWithoutTtl.add(keyName)
                }
            }
            is RedisKeyTtlStatus.Expiring -> {
                expiringKeyCount += 1
                totalExpiringTtlSeconds += ttlStatus.seconds
                incrementBucket(ttlBucketLabel(ttlStatus.seconds))
            }
            is RedisKeyTtlStatus.Unknown -> {
                unknownTtlKeyCount += 1
                incrementBucket(TTL_BUCKET_UNKNOWN)
            }
        }
    }

    private fun recordMemory(memoryUsage: RedisKeyMemoryUsage) {
        when (memoryUsage) {
            RedisKeyMemoryUsage.Unknown -> Unit
            is RedisKeyMemoryUsage.Known -> {
                knownMemoryBytes += memoryUsage.bytes
                knownMemoryKeyCount += 1
            }
        }
    }

    private fun recordNamingAnomaly(keyName: String) {
        val reason = namingAnomalyReason(keyName) ?: return
        namingAnomalyCount += 1

        if (sampleAnomalousKeys.size < MAX_SAMPLE_KEY_COUNT) {
            sampleAnomalousKeys.add(keyName)
        }
        if (namingAnomalies.size < MAX_SAMPLE_KEY_COUNT) {
            namingAnomalies.add(
                NamespaceNamingAnomaly(
                    keyName = keyName,
                    reason = reason,
                )
            )
        }
    }

    private fun recordChildPattern(keyName: String) {
        val keySegments = namespaceResolver.splitSegments(keyName)
        val namespaceSegments = namespaceResolver.splitSegments(identity.displayName)
        val childSegment = keySegments.getOrNull(namespaceSegments.size)
            ?: keySegments.firstOrNull()
            ?: return

        childPatternCounts.merge(childSegment, 1L, Long::plus)
    }

    private fun ttlCoverage(): NamespaceTtlCoverage {
        val knownTtlStateCount = expiringKeyCount + noTtlKeyCount
        val coveragePercent = if (knownTtlStateCount == 0L) {
            0.0
        } else {
            expiringKeyCount.toDouble() / knownTtlStateCount.toDouble() * 100.0
        }

        return NamespaceTtlCoverage(
            expiringKeyCount = expiringKeyCount,
            noTtlKeyCount = noTtlKeyCount,
            unknownTtlKeyCount = unknownTtlKeyCount,
            coveragePercent = coveragePercent,
        )
    }

    private fun memoryUsage(): NamespaceMemoryUsage {
        if (knownMemoryKeyCount == 0L) {
            return NamespaceMemoryUsage.Unknown
        }

        val sampledKeys = if (knownMemoryKeyCount == keyCount) {
            null
        } else {
            knownMemoryKeyCount
        }

        return NamespaceMemoryUsage.Estimated(
            totalBytes = knownMemoryBytes,
            sampledKeys = sampledKeys,
        )
    }

    private fun averageTtlSeconds(): Long? {
        if (expiringKeyCount == 0L) {
            return null
        }

        return totalExpiringTtlSeconds / expiringKeyCount
    }

    private fun memoryConcentrationPercent(totalKnownMemoryBytes: Long): Double {
        if (totalKnownMemoryBytes <= 0L || knownMemoryBytes <= 0L) {
            return 0.0
        }

        return knownMemoryBytes.toDouble() / totalKnownMemoryBytes.toDouble() * 100.0
    }

    private fun unexpectedSignal(): UnexpectedNamespaceSignal? {
        val reason = unexpectedReason ?: return null
        return UnexpectedNamespaceSignal(
            namespaceName = identity.displayName,
            reason = reason,
        )
    }

    private fun incrementBucket(label: String) {
        ttlBucketCounts[label] = ttlBucketCounts.getValue(label) + 1L
    }

    private fun ttlBucketLabel(ttlSeconds: Long): String {
        return when {
            ttlSeconds < 3_600L -> TTL_BUCKET_LT_1H
            ttlSeconds < 86_400L -> TTL_BUCKET_LT_1D
            ttlSeconds < 604_800L -> TTL_BUCKET_LT_7D
            else -> TTL_BUCKET_GTE_7D
        }
    }

    private fun namingAnomalyReason(keyName: String): String? {
        return when {
            !namespaceAnalysisSettings.allowWhitespaceInKeys && keyName.any { it.isWhitespace() } -> {
                "Key contains whitespace"
            }
            !namespaceAnalysisSettings.allowUppercaseInKeys && keyName.any { it.isUpperCase() } -> {
                "Key contains uppercase characters"
            }
            !namespaceAnalysisSettings.allowRepeatedDelimiters && namespaceResolver.containsRepeatedDelimiter(keyName) -> {
                "Key contains repeated delimiters"
            }
            keyStartsOrEndsWithDelimiter(keyName) -> {
                "Key starts or ends with the namespace delimiter"
            }
            else -> null
        }
    }

    private fun keyStartsOrEndsWithDelimiter(keyName: String): Boolean {
        return namespaceAnalysisSettings.normalizedDelimiters.any { delimiter ->
            keyName.startsWith(delimiter) || keyName.endsWith(delimiter)
        }
    }

    private companion object {
        const val MAX_SAMPLE_KEY_COUNT = 5
        const val MAX_DETAIL_PATTERN_COUNT = 5
        const val TTL_BUCKET_NO_TTL = "No TTL"
        const val TTL_BUCKET_LT_1H = "< 1 hour"
        const val TTL_BUCKET_LT_1D = "< 1 day"
        const val TTL_BUCKET_LT_7D = "< 7 days"
        const val TTL_BUCKET_GTE_7D = ">= 7 days"
        const val TTL_BUCKET_UNKNOWN = "Unknown"
    }
}

private const val INITIAL_CURSOR = "0"
private const val DEFAULT_PATTERN = "*"
private const val DEFAULT_SCAN_COUNT = 100L
private const val DEFAULT_TOP_RISKY_NAMESPACE_COUNT = 5