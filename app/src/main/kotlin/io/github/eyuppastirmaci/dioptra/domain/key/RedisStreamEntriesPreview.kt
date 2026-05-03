package io.github.eyuppastirmaci.dioptra.domain.key

data class RedisStreamFieldCell(
    val field: String,
    val valuePreview: RedisStringValuePreview,
)

data class RedisStreamEntryRow(
    val entryId: String,
    val fields: List<RedisStreamFieldCell>,
)

data class RedisStreamEntriesPreview(
    val rows: List<RedisStreamEntryRow>,
    val truncated: Boolean,
    /** Exclusive minimum stream ID for the next [XRANGE] page; null when no further page is offered. */
    val loadMoreAfterId: String?,
)
