package io.github.eyuppastirmaci.dioptra.domain.key

data class RedisListItemRow(
    /** Redis list index (head-first sample starts at 0). */
    val index: Long,
    val rawValue: ByteArray,
    val valuePreview: RedisStringValuePreview,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RedisListItemRow) return false

        return index == other.index &&
            rawValue.contentEquals(other.rawValue) &&
            valuePreview == other.valuePreview
    }

    override fun hashCode(): Int {
        var result = index.hashCode()
        result = 31 * result + rawValue.contentHashCode()
        result = 31 * result + valuePreview.hashCode()
        return result
    }
}

data class RedisListItemsPreview(
    val rows: List<RedisListItemRow>,
    val truncated: Boolean,
    /** Next [LRANGE] start index when more list elements may exist. */
    val nextStartIndex: Long?,
)
