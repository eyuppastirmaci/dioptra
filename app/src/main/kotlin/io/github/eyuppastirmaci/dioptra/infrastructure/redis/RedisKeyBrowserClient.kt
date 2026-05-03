package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class RedisKeyBrowserClient(
    private val commands: RedisCommands<String, String>,
) {

    /**
     * Scans a single Redis key page using the given cursor, pattern, and count hint.
     */
    suspend fun scanPage(request: RedisKeyScanRequest = RedisKeyScanRequest()): RedisKeyScanPage {
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

            RedisKeyScanPage(
                cursor = request.cursor,
                nextCursor = scanCursor.cursor,
                finished = scanCursor.isFinished,
                keys = keys,
            )
        }
    }

    private fun fetchKeyMetadata(key: String): RedisRawKeyMetadata {
        return RedisRawKeyMetadata(
            name = key,
            type = commands.type(key),
            ttlSeconds = commands.ttl(key),
            memoryUsageBytes = fetchMemoryUsage(key),
        )
    }

    private fun fetchMemoryUsage(key: String): Long? {
        return try {
            commands.memoryUsage(key)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            null
        }
    }
}

data class RedisKeyScanRequest(
    val cursor: String = INITIAL_SCAN_CURSOR,
    val pattern: String = DEFAULT_SCAN_PATTERN,
    val count: Long = DEFAULT_SCAN_COUNT,
)

data class RedisKeyScanPage(
    val cursor: String,
    val nextCursor: String,
    val finished: Boolean,
    val keys: List<RedisRawKeyMetadata>,
) {

    val hasMore: Boolean
        get() = !finished
}

data class RedisRawKeyMetadata(
    val name: String,
    val type: String,
    val ttlSeconds: Long,
    val memoryUsageBytes: Long?,
)

private const val INITIAL_SCAN_CURSOR = "0"
private const val DEFAULT_SCAN_PATTERN = "*"
private const val DEFAULT_SCAN_COUNT = 20L