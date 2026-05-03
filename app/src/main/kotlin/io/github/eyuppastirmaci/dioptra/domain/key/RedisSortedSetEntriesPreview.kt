package io.github.eyuppastirmaci.dioptra.domain.key

data class RedisSortedSetEntryRow(
    val score: Double,
    val memberPreview: RedisStringValuePreview,
)

data class RedisSortedSetEntriesPreview(
    val rows: List<RedisSortedSetEntryRow>,
    /** Entries already fetched but not shown in [rows]; drain before the next [ZSCAN]. */
    val overflow: List<RedisSortedSetEntryRow>,
    val truncated: Boolean,
    /** Non-null while [ZSCAN] can still return more entries. */
    val loadMoreCursor: String?,
)
