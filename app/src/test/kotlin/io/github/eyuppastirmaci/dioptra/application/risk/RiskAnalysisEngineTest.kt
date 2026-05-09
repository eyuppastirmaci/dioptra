package io.github.eyuppastirmaci.dioptra.application.risk

import io.github.eyuppastirmaci.dioptra.application.namespace.NamespaceResolver
import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.config.RiskAnalysisSettings
import io.github.eyuppastirmaci.dioptra.domain.risk.RedisMemoryPolicySnapshot
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskLevel
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskReason
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisRawRiskKeyMetadata
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisRiskScanPage
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisRiskScanRequest
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisRiskScanSource
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisMemoryUsageMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTtlMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTypeMapper
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiskAnalysisEngineTest {

    @Test
    fun `detects no ttl big keys and top no ttl keys`() = runBlocking {
        val engine = createEngine(
            settings = RiskAnalysisSettings(
                bigKeyThresholdBytes = 1_000,
                topKeyCount = 2,
            ),
            keys = listOf(
                rawKey(name = "cache:a", ttlSeconds = -1, memoryUsageBytes = 2_000),
                rawKey(name = "cache:b", ttlSeconds = -1, memoryUsageBytes = 500),
                rawKey(name = "cache:c", ttlSeconds = 60, memoryUsageBytes = 3_000),
            ),
        )

        val snapshot = engine.analyze()

        assertEquals(3L, snapshot.analyzedKeyCount)
        assertEquals(2L, snapshot.noTtlKeyCount)
        assertEquals(2L, snapshot.bigKeyCount)
        assertEquals(listOf("cache:c", "cache:a"), snapshot.topLargestKeys.map { it.name })
        assertEquals(listOf("cache:a", "cache:b"), snapshot.topNoTtlKeys.map { it.name })
        assertTrue(RiskReason.NO_TTL in snapshot.topNoTtlKeys.first().riskReasons)
        assertTrue(RiskReason.BIG_KEY in snapshot.topLargestKeys.first().riskReasons)
    }

    @Test
    fun `detects large collection types`() = runBlocking {
        val engine = createEngine(
            settings = RiskAnalysisSettings(
                largeHashFieldThreshold = 10,
                largeListLengthThreshold = 10,
                largeSetMemberThreshold = 10,
                largeZsetMemberThreshold = 10,
                largeStreamEntryThreshold = 10,
            ),
            keys = listOf(
                rawKey(name = "hash:1", type = "hash", collectionSize = 10),
                rawKey(name = "list:1", type = "list", collectionSize = 11),
                rawKey(name = "set:1", type = "set", collectionSize = 12),
                rawKey(name = "zset:1", type = "zset", collectionSize = 13),
                rawKey(name = "stream:1", type = "stream", collectionSize = 14),
            ),
        )

        val snapshot = engine.analyze()

        assertEquals(1L, snapshot.largeHashCount)
        assertEquals(1L, snapshot.largeListCount)
        assertEquals(1L, snapshot.largeSetCount)
        assertEquals(1L, snapshot.largeZsetCount)
        assertEquals(1L, snapshot.largeStreamCount)
        assertEquals(5L, snapshot.largeCollectionKeyCount)
    }

    @Test
    fun `groups risky patterns by namespace`() = runBlocking {
        val engine = createEngine(
            namespaceSettings = NamespaceAnalysisSettings(namespaceDepth = 1),
            settings = RiskAnalysisSettings(bigKeyThresholdBytes = 1_000),
            keys = listOf(
                rawKey(name = "session:1", ttlSeconds = -1, memoryUsageBytes = 2_000),
                rawKey(name = "session:2", ttlSeconds = -1, memoryUsageBytes = 100),
                rawKey(name = "cache:1", ttlSeconds = 60, memoryUsageBytes = 200),
            ),
        )

        val snapshot = engine.analyze()
        val session = snapshot.riskyPatterns.single { it.patternName == "session" }

        assertEquals(2L, session.keyCount)
        assertEquals(2L, session.noTtlKeyCount)
        assertEquals(1L, session.bigKeyCount)
        assertEquals(RiskLevel.CRITICAL, session.riskLevel)
    }

    @Test
    fun `builds maxmemory and eviction warnings`() = runBlocking {
        val engine = createEngine(
            keys = listOf(
                rawKey(name = "cache:a", ttlSeconds = -1),
                rawKey(name = "cache:b", ttlSeconds = -1),
            ),
            memoryPolicy = RedisMemoryPolicySnapshot(
                usedMemoryBytes = 900,
                maxmemoryBytes = 1_000,
                maxmemoryPolicy = "volatile-lru",
                evictedKeys = 3,
            ),
        )

        val snapshot = engine.analyze()

        assertTrue(snapshot.warnings.any { it.message.contains("80%") })
        assertTrue(snapshot.warnings.any { it.message.contains("evicted keys") })
        assertTrue(snapshot.warnings.any { it.message.contains("Volatile eviction") })
    }

    @Test
    fun `warns about noeviction with active maxmemory`() = runBlocking {
        val engine = createEngine(
            memoryPolicy = RedisMemoryPolicySnapshot(
                usedMemoryBytes = 100,
                maxmemoryBytes = 1_000,
                maxmemoryPolicy = "noeviction",
                evictedKeys = 0,
            ),
        )

        val snapshot = engine.analyze()

        assertTrue(snapshot.warnings.any { it.level == RiskLevel.CRITICAL && it.message.contains("noeviction") })
    }

    private fun createEngine(
        namespaceSettings: NamespaceAnalysisSettings = NamespaceAnalysisSettings(),
        settings: RiskAnalysisSettings = RiskAnalysisSettings(),
        keys: List<RedisRawRiskKeyMetadata> = emptyList(),
        memoryPolicy: RedisMemoryPolicySnapshot = RedisMemoryPolicySnapshot(
            usedMemoryBytes = null,
            maxmemoryBytes = 0,
            maxmemoryPolicy = "noeviction",
            evictedKeys = 0,
        ),
    ): RiskAnalysisEngine {
        return RiskAnalysisEngine(
            redisRiskScanSource = FakeRedisRiskScanSource(keys, memoryPolicy),
            redisTypeMapper = RedisTypeMapper(),
            redisTtlMapper = RedisTtlMapper(),
            redisMemoryUsageMapper = RedisMemoryUsageMapper(),
            namespaceResolver = NamespaceResolver(namespaceSettings),
            riskAnalysisSettings = settings,
        )
    }

    private fun rawKey(
        name: String,
        type: String = "string",
        ttlSeconds: Long = 60,
        memoryUsageBytes: Long? = 128,
        collectionSize: Long? = null,
    ): RedisRawRiskKeyMetadata {
        return RedisRawRiskKeyMetadata(
            name = name,
            type = type,
            ttlSeconds = ttlSeconds,
            memoryUsageBytes = memoryUsageBytes,
            collectionSize = collectionSize,
        )
    }
}

private class FakeRedisRiskScanSource(
    private val keys: List<RedisRawRiskKeyMetadata>,
    private val memoryPolicy: RedisMemoryPolicySnapshot,
) : RedisRiskScanSource {

    override suspend fun scanPage(request: RedisRiskScanRequest): RedisRiskScanPage {
        return RedisRiskScanPage(
            cursor = request.cursor,
            nextCursor = request.cursor,
            finished = true,
            keys = keys,
        )
    }

    override suspend fun fetchMemoryPolicy(): RedisMemoryPolicySnapshot {
        return memoryPolicy
    }
}
