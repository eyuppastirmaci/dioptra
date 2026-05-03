package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.lettuce.core.Limit
import io.lettuce.core.Range
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RedisCollectionScanPage<T>(
    /** Up to [limit] entries for the current UI page. */
    val items: List<T>,
    /**
     * Entries already returned by Redis but beyond [limit]; drain client-side before the next SCAN call.
     */
    val overflow: List<T>,
    /**
     * Cursor token for the next SCAN round-trip; null when [scanExhausted] is true (nothing left at Redis).
     */
    val nextResumeCursor: String?,
    val scanExhausted: Boolean,
)

class RedisKeyDetailClient(
    private val commands: RedisCommands<String, ByteArray>,
) {

    suspend fun getStringValue(key: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            commands.get(key)
        }
    }

    suspend fun hashFieldCount(key: String): Long? = redisLongOrNull { commands.hlen(key) }

    /**
     * Paginated [HSCAN]: resumes from [resumeCursor] (null = start).
     */
    suspend fun hashFieldsScanPage(
        key: String,
        resumeCursor: String?,
        limit: Int,
    ): RedisCollectionScanPage<Pair<String, ByteArray>>? {
        if (limit <= 0) {
            return RedisCollectionScanPage(emptyList(), emptyList(), null, true)
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val collected = mutableListOf<Pair<String, ByteArray>>()
                var cursor = scanCursorFromResume(resumeCursor)
                var exhausted = false
                while (true) {
                    val scan = commands.hscan(key, cursor)
                    cursor = ScanCursor.of(scan.cursor)
                    exhausted = scan.isFinished
                    for ((field, value) in scan.map) {
                        collected += field to value
                    }
                    if (exhausted || collected.size >= limit) break
                }
                val items = collected.take(limit)
                val overflow = collected.drop(limit)
                RedisCollectionScanPage(
                    items = items,
                    overflow = overflow,
                    nextResumeCursor = if (exhausted) null else cursor.cursor,
                    scanExhausted = exhausted,
                )
            }.getOrNull()
        }
    }

    suspend fun listLength(key: String): Long? = redisLongOrNull { commands.llen(key) }

    suspend fun listRange(key: String, start: Long, count: Int): List<ByteArray>? {
        if (count <= 0) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                commands.lrange(key, start, start + count - 1)
            }.getOrNull()
        }
    }

    suspend fun setMemberCount(key: String): Long? = redisLongOrNull { commands.scard(key) }

    suspend fun setMembersScanPage(
        key: String,
        resumeCursor: String?,
        limit: Int,
    ): RedisCollectionScanPage<ByteArray>? {
        if (limit <= 0) {
            return RedisCollectionScanPage(emptyList(), emptyList(), null, true)
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val collected = mutableListOf<ByteArray>()
                var cursor = scanCursorFromResume(resumeCursor)
                var exhausted = false
                while (true) {
                    val scan = commands.sscan(key, cursor)
                    cursor = ScanCursor.of(scan.cursor)
                    exhausted = scan.isFinished
                    for (member in scan.values) {
                        collected += member
                    }
                    if (exhausted || collected.size >= limit) break
                }
                val items = collected.take(limit)
                val overflow = collected.drop(limit)
                RedisCollectionScanPage(
                    items = items,
                    overflow = overflow,
                    nextResumeCursor = if (exhausted) null else cursor.cursor,
                    scanExhausted = exhausted,
                )
            }.getOrNull()
        }
    }

    suspend fun sortedSetMemberCount(key: String): Long? = redisLongOrNull { commands.zcard(key) }

    suspend fun sortedSetScanPage(
        key: String,
        resumeCursor: String?,
        limit: Int,
    ): RedisCollectionScanPage<Pair<ByteArray, Double>>? {
        if (limit <= 0) {
            return RedisCollectionScanPage(emptyList(), emptyList(), null, true)
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val collected = mutableListOf<Pair<ByteArray, Double>>()
                var cursor = scanCursorFromResume(resumeCursor)
                var exhausted = false
                while (true) {
                    val scan = commands.zscan(key, cursor)
                    cursor = ScanCursor.of(scan.cursor)
                    exhausted = scan.isFinished
                    for (scored in scan.values) {
                        collected += scored.value to scored.score
                    }
                    if (exhausted || collected.size >= limit) break
                }
                val items = collected.take(limit)
                val overflow = collected.drop(limit)
                RedisCollectionScanPage(
                    items = items,
                    overflow = overflow,
                    nextResumeCursor = if (exhausted) null else cursor.cursor,
                    scanExhausted = exhausted,
                )
            }.getOrNull()
        }
    }

    suspend fun streamLength(key: String): Long? = redisLongOrNull { commands.xlen(key) }

    /**
     * Oldest-first stream slice: either head ([afterIdExclusive] null) or entries strictly after [afterIdExclusive].
     */
    suspend fun streamEntriesRangePage(
        key: String,
        afterIdExclusive: String?,
        limit: Int,
    ): List<Pair<String, Map<String, ByteArray>>>? {
        if (limit <= 0) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val range = when {
                    afterIdExclusive.isNullOrEmpty() ->
                        Range.unbounded<String>()
                    else ->
                        Range.from(
                            Range.Boundary.excluding(afterIdExclusive),
                            Range.Boundary.unbounded(),
                        )
                }
                commands.xrange(key, range, Limit.from(limit.toLong()))
                    .map { message ->
                        message.id.toString() to message.body
                    }
            }.getOrNull()
        }
    }

    private suspend fun redisLongOrNull(command: () -> Long): Long? {
        return withContext(Dispatchers.IO) {
            runCatching { command() }.getOrNull()
        }
    }

    private fun scanCursorFromResume(resumeCursor: String?): ScanCursor =
        if (resumeCursor.isNullOrEmpty()) {
            ScanCursor.INITIAL
        } else {
            ScanCursor.of(resumeCursor)
        }
}
