package io.github.eyuppastirmaci.dioptra.domain.key

data class RedisListItemRow(
    /** Redis list index (head-first sample starts at 0). */
    val index: Long,
    val valuePreview: RedisStringValuePreview,
)

data class RedisListItemsPreview(
    val rows: List<RedisListItemRow>,
    val truncated: Boolean,
    /** Next [LRANGE] start index when more list elements may exist. */
    val nextStartIndex: Long?,
)
