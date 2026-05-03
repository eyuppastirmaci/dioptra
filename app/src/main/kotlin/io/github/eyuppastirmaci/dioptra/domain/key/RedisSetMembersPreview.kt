package io.github.eyuppastirmaci.dioptra.domain.key

data class RedisSetMemberRow(
    val valuePreview: RedisStringValuePreview,
)

data class RedisSetMembersPreview(
    val rows: List<RedisSetMemberRow>,
    /** Members already fetched but not shown in [rows]; drain before the next [SSCAN]. */
    val overflow: List<RedisSetMemberRow>,
    val truncated: Boolean,
    /** Non-null while [SSCAN] can still return more members. */
    val loadMoreCursor: String?,
)
