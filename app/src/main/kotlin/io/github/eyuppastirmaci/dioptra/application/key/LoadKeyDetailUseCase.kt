package io.github.eyuppastirmaci.dioptra.application.key

import io.github.eyuppastirmaci.dioptra.domain.key.RedisCollectionSizeKind
import io.github.eyuppastirmaci.dioptra.domain.key.RedisCollectionSizeSummary
import io.github.eyuppastirmaci.dioptra.domain.key.RedisHashFieldRow
import io.github.eyuppastirmaci.dioptra.domain.key.RedisHashFieldsPreview
import io.github.eyuppastirmaci.dioptra.domain.key.RedisListItemRow
import io.github.eyuppastirmaci.dioptra.domain.key.RedisListItemsPreview
import io.github.eyuppastirmaci.dioptra.domain.key.RedisSetMemberRow
import io.github.eyuppastirmaci.dioptra.domain.key.RedisSetMembersPreview
import io.github.eyuppastirmaci.dioptra.domain.key.RedisSortedSetEntriesPreview
import io.github.eyuppastirmaci.dioptra.domain.key.RedisSortedSetEntryRow
import io.github.eyuppastirmaci.dioptra.domain.key.RedisStreamEntriesPreview
import io.github.eyuppastirmaci.dioptra.domain.key.RedisStreamEntryRow
import io.github.eyuppastirmaci.dioptra.domain.key.RedisStreamFieldCell
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyDetailSnapshot
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeySummary
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyType
import io.github.eyuppastirmaci.dioptra.domain.key.RedisStringValuePreview
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyDetailClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.codec.RedisDecodedValue
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.codec.RedisValueDecoder

data class ScanLoadMoreResult<R>(
    val newRows: List<R>,
    val overflow: List<R>,
    val nextCursor: String?,
)

class LoadKeyDetailUseCase(
    private val redisKeyDetailClient: RedisKeyDetailClient,
    private val redisValueDecoder: RedisValueDecoder,
) {

    suspend fun load(key: RedisKeySummary): RedisKeyDetailSnapshot {
        val collectionSize = loadCollectionSize(key)
        return RedisKeyDetailSnapshot(
            key = key,
            stringValuePreview = if (key.type == RedisKeyType.STRING) {
                redisKeyDetailClient.getStringValue(key.name)?.let(redisValueDecoder::decode)?.toPreview()
            } else {
                null
            },
            collectionSize = collectionSize,
            hashFieldsPreview = loadHashFieldsPreview(key, collectionSize),
            listItemsPreview = loadListItemsPreview(key, collectionSize),
            setMembersPreview = loadSetMembersPreview(key, collectionSize),
            sortedSetEntriesPreview = loadSortedSetEntriesPreview(key, collectionSize),
            streamEntriesPreview = loadStreamEntriesPreview(key, collectionSize),
        )
    }

    suspend fun loadMoreHashFields(
        keyName: String,
        cursor: String,
    ): ScanLoadMoreResult<RedisHashFieldRow>? {
        val page = redisKeyDetailClient.hashFieldsScanPage(keyName, cursor, HASH_FIELD_SAMPLE_CAP)
            ?: return null
        return ScanLoadMoreResult(
            newRows = decodeHashRows(page.items),
            overflow = decodeHashRows(page.overflow),
            nextCursor = page.nextResumeCursor,
        )
    }

    suspend fun loadMoreListItems(
        keyName: String,
        startIndex: Long,
    ): Pair<List<RedisListItemRow>, Long?>? {
        val rawElements =
            redisKeyDetailClient.listRange(keyName, startIndex, LIST_ITEM_SAMPLE_CAP) ?: return null
        if (rawElements.isEmpty()) {
            return emptyList<RedisListItemRow>() to null
        }
        val rows = rawElements.mapIndexed { offset, bytes ->
            RedisListItemRow(
                index = startIndex + offset,
                rawValue = bytes,
                valuePreview = redisValueDecoder.decode(bytes).toPreview(),
            )
        }
        val knownLen = redisKeyDetailClient.listLength(keyName)
        val nextStart =
            listNextStartIndex(startIndex, rawElements.size, LIST_ITEM_SAMPLE_CAP, knownLen)
        return rows to nextStart
    }

    suspend fun loadMoreSetMembers(
        keyName: String,
        cursor: String,
    ): ScanLoadMoreResult<RedisSetMemberRow>? {
        val page =
            redisKeyDetailClient.setMembersScanPage(keyName, cursor, SET_MEMBER_SAMPLE_CAP) ?: return null
        return ScanLoadMoreResult(
            newRows = decodeSetRows(page.items),
            overflow = decodeSetRows(page.overflow),
            nextCursor = page.nextResumeCursor,
        )
    }

    suspend fun loadMoreSortedSetEntries(
        keyName: String,
        cursor: String,
    ): ScanLoadMoreResult<RedisSortedSetEntryRow>? {
        val page =
            redisKeyDetailClient.sortedSetScanPage(keyName, cursor, ZSET_ENTRY_SAMPLE_CAP) ?: return null
        return ScanLoadMoreResult(
            newRows = decodeSortedSetRows(page.items),
            overflow = decodeSortedSetRows(page.overflow),
            nextCursor = page.nextResumeCursor,
        )
    }

    suspend fun loadMoreStreamEntries(
        keyName: String,
        afterIdExclusive: String,
    ): Pair<List<RedisStreamEntryRow>, String?>? {
        val rawEntries =
            redisKeyDetailClient.streamEntriesRangePage(
                keyName,
                afterIdExclusive,
                STREAM_ENTRY_SAMPLE_CAP,
            ) ?: return null
        if (rawEntries.isEmpty()) {
            return emptyList<RedisStreamEntryRow>() to null
        }
        val rows = decodeStreamRows(rawEntries)
        val nextAfter = streamAppendNextAfterId(rawEntries, STREAM_ENTRY_SAMPLE_CAP)
        return rows to nextAfter
    }

    private suspend fun loadStreamEntriesPreview(
        key: RedisKeySummary,
        collectionSize: RedisCollectionSizeSummary?,
    ): RedisStreamEntriesPreview? {
        if (key.type != RedisKeyType.STREAM) {
            return null
        }

        val rawEntries =
            redisKeyDetailClient.streamEntriesRangePage(key.name, null, STREAM_ENTRY_SAMPLE_CAP)
                ?: return null

        val rows = decodeStreamRows(rawEntries)
        val knownTotal = collectionSize.knownMemberCountOrNull()
        val loadMoreAfterId =
            streamInitialNextAfterId(rawEntries, STREAM_ENTRY_SAMPLE_CAP, knownTotal)

        val truncated = when (collectionSize) {
            is RedisCollectionSizeSummary.Known ->
                rows.size < collectionSize.memberCount || loadMoreAfterId != null
            else ->
                loadMoreAfterId != null
        }

        return RedisStreamEntriesPreview(
            rows = rows,
            truncated = truncated,
            loadMoreAfterId = loadMoreAfterId,
        )
    }

    private suspend fun loadSortedSetEntriesPreview(
        key: RedisKeySummary,
        collectionSize: RedisCollectionSizeSummary?,
    ): RedisSortedSetEntriesPreview? {
        if (key.type != RedisKeyType.ZSET) {
            return null
        }

        val page =
            redisKeyDetailClient.sortedSetScanPage(key.name, null, ZSET_ENTRY_SAMPLE_CAP) ?: return null

        val rows = decodeSortedSetRows(page.items)
        val overflowRows = decodeSortedSetRows(page.overflow)

        val loadMoreCursor = page.nextResumeCursor
        val truncated = collectionTruncatedScan(
            loadedCount = rows.size + overflowRows.size,
            knownMemberCount = collectionSize.knownMemberCountOrNull(),
            scanHasMore = loadMoreCursor != null,
        )

        return RedisSortedSetEntriesPreview(
            rows = rows,
            overflow = overflowRows,
            truncated = truncated,
            loadMoreCursor = loadMoreCursor,
        )
    }

    private suspend fun loadSetMembersPreview(
        key: RedisKeySummary,
        collectionSize: RedisCollectionSizeSummary?,
    ): RedisSetMembersPreview? {
        if (key.type != RedisKeyType.SET) {
            return null
        }

        val page =
            redisKeyDetailClient.setMembersScanPage(key.name, null, SET_MEMBER_SAMPLE_CAP) ?: return null

        val rows = decodeSetRows(page.items)
        val overflowRows = decodeSetRows(page.overflow)

        val loadMoreCursor = page.nextResumeCursor
        val truncated = collectionTruncatedScan(
            loadedCount = rows.size + overflowRows.size,
            knownMemberCount = collectionSize.knownMemberCountOrNull(),
            scanHasMore = loadMoreCursor != null,
        )

        return RedisSetMembersPreview(
            rows = rows,
            overflow = overflowRows,
            truncated = truncated,
            loadMoreCursor = loadMoreCursor,
        )
    }

    private suspend fun loadListItemsPreview(
        key: RedisKeySummary,
        collectionSize: RedisCollectionSizeSummary?,
    ): RedisListItemsPreview? {
        if (key.type != RedisKeyType.LIST) {
            return null
        }

        val rawElements =
            redisKeyDetailClient.listRange(key.name, 0L, LIST_ITEM_SAMPLE_CAP) ?: return null

        val knownLen = collectionSize.knownMemberCountOrNull()

        val rows = rawElements.mapIndexed { index, bytes ->
            RedisListItemRow(
                index = index.toLong(),
                rawValue = bytes,
                valuePreview = redisValueDecoder.decode(bytes).toPreview(),
            )
        }

        val nextStartIndex =
            listNextStartIndex(0L, rawElements.size, LIST_ITEM_SAMPLE_CAP, knownLen)

        val truncated = when (collectionSize) {
            is RedisCollectionSizeSummary.Known ->
                rows.size < collectionSize.memberCount || nextStartIndex != null
            else ->
                nextStartIndex != null || rawElements.size >= LIST_ITEM_SAMPLE_CAP
        }

        return RedisListItemsPreview(
            rows = rows,
            truncated = truncated,
            nextStartIndex = nextStartIndex,
        )
    }

    private suspend fun loadHashFieldsPreview(
        key: RedisKeySummary,
        collectionSize: RedisCollectionSizeSummary?,
    ): RedisHashFieldsPreview? {
        if (key.type != RedisKeyType.HASH) {
            return null
        }

        val page =
            redisKeyDetailClient.hashFieldsScanPage(key.name, null, HASH_FIELD_SAMPLE_CAP) ?: return null

        val rows = decodeHashRows(page.items)
        val overflowRows = decodeHashRows(page.overflow)

        val loadMoreCursor = page.nextResumeCursor
        val truncated = collectionTruncatedScan(
            loadedCount = rows.size + overflowRows.size,
            knownMemberCount = collectionSize.knownMemberCountOrNull(),
            scanHasMore = loadMoreCursor != null,
        )

        return RedisHashFieldsPreview(
            rows = rows,
            overflow = overflowRows,
            truncated = truncated,
            loadMoreCursor = loadMoreCursor,
        )
    }

    private fun decodeHashRows(entries: List<Pair<String, ByteArray>>): List<RedisHashFieldRow> =
        entries.map { (field, bytes) ->
            RedisHashFieldRow(field = field, valuePreview = redisValueDecoder.decode(bytes).toPreview())
        }

    private fun decodeSetRows(members: List<ByteArray>): List<RedisSetMemberRow> =
        members.map {
            RedisSetMemberRow(
                rawValue = it,
                valuePreview = redisValueDecoder.decode(it).toPreview(),
            )
        }

    private fun decodeSortedSetRows(entries: List<Pair<ByteArray, Double>>): List<RedisSortedSetEntryRow> =
        entries.map { (memberBytes, score) ->
            RedisSortedSetEntryRow(
                score = score,
                rawMember = memberBytes,
                memberPreview = redisValueDecoder.decode(memberBytes).toPreview(),
            )
        }

    private fun decodeStreamRows(
        rawEntries: List<Pair<String, Map<String, ByteArray>>>,
    ): List<RedisStreamEntryRow> =
        rawEntries.map { (entryId, body) ->
            RedisStreamEntryRow(
                entryId = entryId,
                fields = body.entries
                    .sortedBy { entry -> entry.key }
                    .map { entry ->
                        RedisStreamFieldCell(
                            field = entry.key,
                            valuePreview = redisValueDecoder.decode(entry.value).toPreview(),
                        )
                    },
            )
        }

    /** First page only: suppress “load more” when [knownTotal] fits entirely in [batch]. */
    private fun streamInitialNextAfterId(
        batch: List<Pair<String, Map<String, ByteArray>>>,
        chunk: Int,
        knownTotal: Long?,
    ): String? {
        if (batch.isEmpty()) {
            return null
        }
        if (knownTotal != null && batch.size.toLong() >= knownTotal) {
            return null
        }
        if (batch.size < chunk) {
            return null
        }
        return batch.last().first
    }

    /** Subsequent pages: another page exists only when Redis returned a full chunk. */
    private fun streamAppendNextAfterId(
        batch: List<Pair<String, Map<String, ByteArray>>>,
        chunk: Int,
    ): String? {
        if (batch.size < chunk) {
            return null
        }
        return batch.last().first
    }

    private fun listNextStartIndex(
        start: Long,
        loadedCount: Int,
        chunk: Int,
        knownLen: Long?,
    ): Long? {
        if (loadedCount < chunk) {
            return null
        }
        val next = start + loadedCount
        return if (knownLen != null && next >= knownLen) {
            null
        } else {
            next
        }
    }

    private fun collectionTruncatedScan(
        loadedCount: Int,
        knownMemberCount: Long?,
        scanHasMore: Boolean,
    ): Boolean {
        if (scanHasMore) {
            return true
        }
        return when (knownMemberCount) {
            null -> false
            else -> loadedCount < knownMemberCount
        }
    }

    private fun RedisCollectionSizeSummary?.knownMemberCountOrNull(): Long? =
        when (this) {
            is RedisCollectionSizeSummary.Known -> memberCount
            else -> null
        }

    private suspend fun loadCollectionSize(key: RedisKeySummary): RedisCollectionSizeSummary? {
        val kind = collectionKind(key.type) ?: return null
        val count = collectionMemberCount(key)
        return if (count != null) {
            RedisCollectionSizeSummary.Known(
                kind = kind,
                memberCount = count,
            )
        } else {
            RedisCollectionSizeSummary.Unavailable
        }
    }

    private fun collectionKind(type: RedisKeyType): RedisCollectionSizeKind? {
        return when (type) {
            RedisKeyType.HASH -> RedisCollectionSizeKind.HASH_FIELDS
            RedisKeyType.LIST -> RedisCollectionSizeKind.LIST_ELEMENTS
            RedisKeyType.SET -> RedisCollectionSizeKind.SET_MEMBERS
            RedisKeyType.ZSET -> RedisCollectionSizeKind.ZSET_MEMBERS
            RedisKeyType.STREAM -> RedisCollectionSizeKind.STREAM_ENTRIES
            else -> null
        }
    }

    private suspend fun collectionMemberCount(key: RedisKeySummary): Long? {
        val name = key.name
        return when (key.type) {
            RedisKeyType.HASH -> redisKeyDetailClient.hashFieldCount(name)
            RedisKeyType.LIST -> redisKeyDetailClient.listLength(name)
            RedisKeyType.SET -> redisKeyDetailClient.setMemberCount(name)
            RedisKeyType.ZSET -> redisKeyDetailClient.sortedSetMemberCount(name)
            RedisKeyType.STREAM -> redisKeyDetailClient.streamLength(name)
            else -> null
        }
    }

    private companion object {
        const val HASH_FIELD_SAMPLE_CAP = 32
        const val LIST_ITEM_SAMPLE_CAP = 32
        const val SET_MEMBER_SAMPLE_CAP = 32
        const val ZSET_ENTRY_SAMPLE_CAP = 32
        const val STREAM_ENTRY_SAMPLE_CAP = 24
    }

    private fun RedisDecodedValue.toPreview(): RedisStringValuePreview {
        return when (this) {
            is RedisDecodedValue.Text -> RedisStringValuePreview.Text(value)
            is RedisDecodedValue.Json -> RedisStringValuePreview.Json(value)
            is RedisDecodedValue.Binary -> RedisStringValuePreview.Binary(
                hexPreview = hexPreview,
                sizeBytes = sizeBytes,
                hexDumpLines = hexDumpLines,
                hexDumpSampleBytes = hexDumpSampleBytes,
            )
        }
    }
}
