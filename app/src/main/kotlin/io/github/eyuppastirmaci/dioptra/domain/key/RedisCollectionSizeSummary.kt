package io.github.eyuppastirmaci.dioptra.domain.key

enum class RedisCollectionSizeKind(
    val metricLabel: String,
    val nounShort: String,
) {
    HASH_FIELDS(metricLabel = "Hash fields", nounShort = "fields"),
    LIST_ELEMENTS(metricLabel = "List items", nounShort = "items"),
    SET_MEMBERS(metricLabel = "Set members", nounShort = "members"),
    ZSET_MEMBERS(metricLabel = "Sorted set members", nounShort = "members"),
    STREAM_ENTRIES(metricLabel = "Stream entries", nounShort = "entries"),
}

sealed interface RedisCollectionSizeSummary {

    data class Known(
        val kind: RedisCollectionSizeKind,
        val memberCount: Long,
    ) : RedisCollectionSizeSummary

    data object Unavailable : RedisCollectionSizeSummary
}
