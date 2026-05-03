package io.github.eyuppastirmaci.dioptra.application.key

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyBrowserPage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeySummary
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyBrowserClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyScanRequest
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisRawKeyMetadata
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisMemoryUsageMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTtlMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTypeMapper

class BrowseKeysUseCase(
    private val redisKeyBrowserClient: RedisKeyBrowserClient,
    private val redisTypeMapper: RedisTypeMapper,
    private val redisTtlMapper: RedisTtlMapper,
    private val redisMemoryUsageMapper: RedisMemoryUsageMapper,
) {

    /**
     * Loads a single Redis key browser page by using SCAN and mapping Redis metadata into domain models.
     */
    suspend fun browse(request: BrowseKeysRequest = BrowseKeysRequest()): RedisKeyBrowserPage {
        val rawPage = redisKeyBrowserClient.scanPage(
            RedisKeyScanRequest(
                cursor = request.cursor,
                pattern = request.pattern,
                count = request.count,
            )
        )

        return RedisKeyBrowserPage(
            cursor = rawPage.cursor,
            nextCursor = rawPage.nextCursor,
            finished = rawPage.finished,
            pattern = request.pattern,
            count = request.count,
            keys = rawPage.keys.map { rawKeyMetadata -> mapKeySummary(rawKeyMetadata) },
        )
    }

    private fun mapKeySummary(rawKeyMetadata: RedisRawKeyMetadata): RedisKeySummary {
        return RedisKeySummary(
            name = rawKeyMetadata.name,
            type = redisTypeMapper.map(rawKeyMetadata.type),
            ttl = redisTtlMapper.map(rawKeyMetadata.ttlSeconds),
            memoryUsage = redisMemoryUsageMapper.map(rawKeyMetadata.memoryUsageBytes),
        )
    }
}

data class BrowseKeysRequest(
    val cursor: String = INITIAL_SCAN_CURSOR,
    val pattern: String = DEFAULT_SCAN_PATTERN,
    val count: Long = DEFAULT_SCAN_COUNT,
)

private const val INITIAL_SCAN_CURSOR = "0"
private const val DEFAULT_SCAN_PATTERN = "*"
private const val DEFAULT_SCAN_COUNT = 20L