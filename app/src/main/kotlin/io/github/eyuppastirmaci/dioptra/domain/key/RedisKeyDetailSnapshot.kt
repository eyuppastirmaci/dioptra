package io.github.eyuppastirmaci.dioptra.domain.key

data class RedisKeyDetailSnapshot(
    val key: RedisKeySummary,
    val stringValuePreview: RedisStringValuePreview?,
    val collectionSize: RedisCollectionSizeSummary?,
    val hashFieldsPreview: RedisHashFieldsPreview?,
    val listItemsPreview: RedisListItemsPreview?,
    val setMembersPreview: RedisSetMembersPreview?,
    val sortedSetEntriesPreview: RedisSortedSetEntriesPreview?,
    val streamEntriesPreview: RedisStreamEntriesPreview?,
)
