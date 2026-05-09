package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.github.eyuppastirmaci.dioptra.domain.risk.RedisMemoryPolicySnapshot
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisInfoParser
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

interface RedisRiskScanSource {

    suspend fun scanPage(request: RedisRiskScanRequest = RedisRiskScanRequest()): RedisRiskScanPage

    suspend fun fetchMemoryPolicy(): RedisMemoryPolicySnapshot
}

class RedisRiskAnalysisClient(
    private val commands: RedisCommands<String, String>,
    private val redisInfoParser: RedisInfoParser,
) : RedisRiskScanSource {

    override suspend fun scanPage(request: RedisRiskScanRequest): RedisRiskScanPage {
        return withContext(Dispatchers.IO) {
            val scanArgs = ScanArgs.Builder
                .matches(request.pattern)
                .limit(request.count)

            val scanCursor = commands.scan(
                ScanCursor.of(request.cursor),
                scanArgs,
            )

            val keys = scanCursor.keys.map { key ->
                currentCoroutineContext().ensureActive()
                fetchKeyMetadata(key)
            }

            RedisRiskScanPage(
                cursor = request.cursor,
                nextCursor = scanCursor.cursor,
                finished = scanCursor.isFinished,
                keys = keys,
            )
        }
    }

    override suspend fun fetchMemoryPolicy(): RedisMemoryPolicySnapshot {
        return withContext(Dispatchers.IO) {
            val info = redisInfoParser.parse(commands.info())
            RedisMemoryPolicySnapshot(
                usedMemoryBytes = info.long("used_memory"),
                maxmemoryBytes = info.long("maxmemory"),
                maxmemoryPolicy = info.string("maxmemory_policy"),
                evictedKeys = info.long("evicted_keys"),
            )
        }
    }

    private fun fetchKeyMetadata(key: String): RedisRawRiskKeyMetadata {
        val type = commands.type(key)
        return RedisRawRiskKeyMetadata(
            name = key,
            type = type,
            ttlSeconds = commands.ttl(key),
            memoryUsageBytes = fetchMemoryUsage(key),
            collectionSize = fetchCollectionSize(
                key = key,
                type = type,
            ),
        )
    }

    private fun fetchMemoryUsage(key: String): Long? {
        return redisLongOrNull { commands.memoryUsage(key) }
    }

    private fun fetchCollectionSize(
        key: String,
        type: String,
    ): Long? {
        return when (type.lowercase()) {
            "hash" -> redisLongOrNull { commands.hlen(key) }
            "list" -> redisLongOrNull { commands.llen(key) }
            "set" -> redisLongOrNull { commands.scard(key) }
            "zset" -> redisLongOrNull { commands.zcard(key) }
            "stream" -> redisLongOrNull { commands.xlen(key) }
            else -> null
        }
    }

    private fun redisLongOrNull(command: () -> Long): Long? {
        return try {
            command()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            null
        }
    }
}

data class RedisRiskScanRequest(
    val cursor: String = INITIAL_SCAN_CURSOR,
    val pattern: String = DEFAULT_SCAN_PATTERN,
    val count: Long = DEFAULT_SCAN_COUNT,
)

data class RedisRiskScanPage(
    val cursor: String,
    val nextCursor: String,
    val finished: Boolean,
    val keys: List<RedisRawRiskKeyMetadata>,
)

data class RedisRawRiskKeyMetadata(
    val name: String,
    val type: String,
    val ttlSeconds: Long,
    val memoryUsageBytes: Long?,
    val collectionSize: Long?,
)

private const val INITIAL_SCAN_CURSOR = "0"
private const val DEFAULT_SCAN_PATTERN = "*"
private const val DEFAULT_SCAN_COUNT = 100L
