package io.github.eyuppastirmaci.dioptra.domain.key

data class RedisHashFieldRow(
    val field: String,
    val valuePreview: RedisStringValuePreview,
)

data class RedisHashFieldsPreview(
    val rows: List<RedisHashFieldRow>,
    /** Fields already fetched but not shown in [rows]; drain before the next [HSCAN]. */
    val overflow: List<RedisHashFieldRow>,
    val truncated: Boolean,
    /** Non-null while [HSCAN] can still return more fields for this key. */
    val loadMoreCursor: String?,
)
