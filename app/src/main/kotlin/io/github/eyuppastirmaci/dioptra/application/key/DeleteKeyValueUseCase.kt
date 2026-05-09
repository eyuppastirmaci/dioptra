package io.github.eyuppastirmaci.dioptra.application.key

import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyOperationClient

data class DeleteKeyValueRequest(
    val keyName: String,
    val target: DeleteKeyValueTarget,
)

sealed interface DeleteKeyValueTarget {
    data class HashField(
        val field: String,
    ) : DeleteKeyValueTarget

    data class ListItem(
        val index: Long,
    ) : DeleteKeyValueTarget

    data class SetMember(
        val member: ByteArray,
    ) : DeleteKeyValueTarget {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SetMember) return false

            return member.contentEquals(other.member)
        }

        override fun hashCode(): Int {
            return member.contentHashCode()
        }
    }

    data class SortedSetMember(
        val member: ByteArray,
    ) : DeleteKeyValueTarget {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SortedSetMember) return false

            return member.contentEquals(other.member)
        }

        override fun hashCode(): Int {
            return member.contentHashCode()
        }
    }

    data class StreamEntry(
        val entryId: String,
    ) : DeleteKeyValueTarget
}

sealed interface DeleteKeyValueResult {
    data object Deleted : DeleteKeyValueResult

    data object Missing : DeleteKeyValueResult
}

class DeleteKeyValueUseCase(
    private val redisKeyOperationClient: RedisKeyOperationClient,
) {

    suspend fun delete(request: DeleteKeyValueRequest): DeleteKeyValueResult {
        val deletedCount = when (val target = request.target) {
            is DeleteKeyValueTarget.HashField ->
                redisKeyOperationClient.deleteHashField(request.keyName, target.field)

            is DeleteKeyValueTarget.ListItem ->
                redisKeyOperationClient.deleteListItemAtIndex(request.keyName, target.index)

            is DeleteKeyValueTarget.SetMember ->
                redisKeyOperationClient.deleteSetMember(request.keyName, target.member)

            is DeleteKeyValueTarget.SortedSetMember ->
                redisKeyOperationClient.deleteSortedSetMember(request.keyName, target.member)

            is DeleteKeyValueTarget.StreamEntry ->
                redisKeyOperationClient.deleteStreamEntry(request.keyName, target.entryId)
        }

        return if (deletedCount > 0) {
            DeleteKeyValueResult.Deleted
        } else {
            DeleteKeyValueResult.Missing
        }
    }
}
