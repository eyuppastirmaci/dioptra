package io.github.eyuppastirmaci.dioptra.domain.key

data class RedisKeyBrowserPage(
    val cursor: String,
    val nextCursor: String,
    val finished: Boolean,
    val pattern: String,
    val count: Long,
    val keys: List<RedisKeySummary>,
) {

    val hasMore: Boolean
        get() = !finished
}