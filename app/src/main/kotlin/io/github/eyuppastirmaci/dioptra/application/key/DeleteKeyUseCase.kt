package io.github.eyuppastirmaci.dioptra.application.key

import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyOperationClient

data class DeleteKeyRequest(
    val keyName: String,
)

sealed interface DeleteKeyResult {
    data object Deleted : DeleteKeyResult

    data object KeyMissing : DeleteKeyResult
}

class DeleteKeyUseCase(
    private val redisKeyOperationClient: RedisKeyOperationClient,
) {

    suspend fun delete(request: DeleteKeyRequest): DeleteKeyResult {
        return if (redisKeyOperationClient.delete(request.keyName) > 0) {
            DeleteKeyResult.Deleted
        } else {
            DeleteKeyResult.KeyMissing
        }
    }
}
