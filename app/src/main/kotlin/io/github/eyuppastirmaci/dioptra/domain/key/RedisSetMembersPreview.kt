package io.github.eyuppastirmaci.dioptra.domain.key

data class RedisSetMemberRow(
    val rawValue: ByteArray,
    val valuePreview: RedisStringValuePreview,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RedisSetMemberRow) return false

        return rawValue.contentEquals(other.rawValue) &&
            valuePreview == other.valuePreview
    }

    override fun hashCode(): Int {
        var result = rawValue.contentHashCode()
        result = 31 * result + valuePreview.hashCode()
        return result
    }
}

data class RedisSetMembersPreview(
    val rows: List<RedisSetMemberRow>,
    /** Members already fetched but not shown in [rows]; drain before the next [SSCAN]. */
    val overflow: List<RedisSetMemberRow>,
    val truncated: Boolean,
    /** Non-null while [SSCAN] can still return more members. */
    val loadMoreCursor: String?,
)
