package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyScanClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyScanPage
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyScanRequest
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisRawKeyMetadata
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisMemoryUsageMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTtlMapper
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NamespaceAnalysisEngineTest {

    @Test
    fun `ignored key patterns exclude matching keys from analysis`() = runBlocking {
        val engine = createEngine(
            settings = NamespaceAnalysisSettings(
                expectedNamespaces = listOf("user"),
                ignoredKeyPatterns = listOf("tmp:*")
            ),
            keys = listOf(
                rawKey(name = "tmp:job:1"),
                rawKey(name = "user:1"),
            ),
        )

        val snapshot = engine.analyze().snapshot

        assertEquals(1, snapshot.totalNamespaceCount)
        assertEquals(1L, snapshot.analyzedKeyCount)
        assertEquals(1L, snapshot.ignoredKeyCount)
        assertEquals(0L, snapshot.alertSuppressedKeyCount)
        assertEquals(listOf("user"), snapshot.summaries.map { it.identity.displayName })
        assertTrue(snapshot.unexpectedNamespaces.isEmpty())
    }

    @Test
    fun `allowed key patterns keep keys visible while suppressing anomaly and unexpected alarms`() = runBlocking {
        val engine = createEngine(
            settings = NamespaceAnalysisSettings(
                expectedNamespaces = listOf("user"),
                allowedKeyPatterns = listOf("Bull Job:*")
            ),
            keys = listOf(
                rawKey(name = "Bull Job:1"),
            ),
        )

        val snapshot = engine.analyze().snapshot
        val summary = snapshot.summaries.single()

        assertEquals(1L, snapshot.analyzedKeyCount)
        assertEquals(0L, snapshot.ignoredKeyCount)
        assertEquals(1L, snapshot.alertSuppressedKeyCount)
        assertEquals(1L, summary.keyCount)
        assertEquals("bull job", summary.identity.normalizedName)
        assertTrue(summary.namingAnomalies.isEmpty())
        assertNull(summary.unexpectedNamespaceSignal)
        assertTrue(snapshot.unexpectedNamespaces.isEmpty())
        assertTrue(snapshot.anomalousNamespaces.isEmpty())
    }

    private fun createEngine(
        settings: NamespaceAnalysisSettings,
        keys: List<RedisRawKeyMetadata>,
    ): NamespaceAnalysisEngine {
        return NamespaceAnalysisEngine(
            redisKeyBrowserClient = FakeRedisKeyScanClient(keys),
            redisTtlMapper = RedisTtlMapper(),
            redisMemoryUsageMapper = RedisMemoryUsageMapper(),
            namespaceAnalysisSettings = settings,
            namespaceResolver = NamespaceResolver(settings = settings),
            namespaceHealthScorer = NamespaceHealthScorer(),
        )
    }

    private fun rawKey(
        name: String,
        ttlSeconds: Long = -1L,
        memoryUsageBytes: Long? = 128L,
    ): RedisRawKeyMetadata {
        return RedisRawKeyMetadata(
            name = name,
            type = "string",
            ttlSeconds = ttlSeconds,
            memoryUsageBytes = memoryUsageBytes,
        )
    }
}

private class FakeRedisKeyScanClient(
    private val keys: List<RedisRawKeyMetadata>,
) : RedisKeyScanClient {

    override suspend fun scanPage(request: RedisKeyScanRequest): RedisKeyScanPage {
        return RedisKeyScanPage(
            cursor = request.cursor,
            nextCursor = request.cursor,
            finished = true,
            keys = keys,
        )
    }
}