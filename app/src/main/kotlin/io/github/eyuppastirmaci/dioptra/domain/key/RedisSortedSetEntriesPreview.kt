package io.github.eyuppastirmaci.dioptra.domain.key

data class RedisSortedSetEntryRow(
    val score: Double,
    val rawMember: ByteArray,
    val memberPreview: RedisStringValuePreview,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RedisSortedSetEntryRow) return false

        return score == other.score &&
            rawMember.contentEquals(other.rawMember) &&
            memberPreview == other.memberPreview
    }

    override fun hashCode(): Int {
        var result = score.hashCode()
        result = 31 * result + rawMember.contentHashCode()
        result = 31 * result + memberPreview.hashCode()
        return result
    }
}

data class RedisSortedSetEntriesPreview(
    val rows: List<RedisSortedSetEntryRow>,
    /** Entries already fetched but not shown in [rows]; drain before the next [ZSCAN]. */
    val overflow: List<RedisSortedSetEntryRow>,
    val truncated: Boolean,
    /** Non-null while [ZSCAN] can still return more entries. */
    val loadMoreCursor: String?,
)
