package io.github.eyuppastirmaci.dioptra.application.key

import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyOperationClient

data class ExpireKeyRequest(
    val keyName: String,
    val seconds: Long,
)

sealed interface ExpireKeyResult {
    data class Updated(
        val seconds: Long,
    ) : ExpireKeyResult

    data object KeyMissing : ExpireKeyResult
}

class ExpireKeyUseCase(
    private val redisKeyOperationClient: RedisKeyOperationClient,
) {

    suspend fun expire(request: ExpireKeyRequest): ExpireKeyResult {
        require(request.seconds > 0) {
            "TTL must be a positive number of seconds."
        }

        return if (redisKeyOperationClient.expire(request.keyName, request.seconds)) {
            ExpireKeyResult.Updated(request.seconds)
        } else {
            ExpireKeyResult.KeyMissing
        }
    }
}
