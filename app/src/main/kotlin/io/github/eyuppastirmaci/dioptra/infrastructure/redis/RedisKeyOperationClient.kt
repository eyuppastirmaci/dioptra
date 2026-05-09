package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.UUID

class RedisKeyOperationClient(
    private val commands: RedisCommands<String, String>,
    private val binaryValueCommands: RedisCommands<String, ByteArray>,
) {

    suspend fun expire(
        key: String,
        seconds: Long,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            commands.expire(key, seconds)
        }
    }

    suspend fun delete(key: String): Long {
        return withContext(Dispatchers.IO) {
            commands.del(key)
        }
    }

    suspend fun deleteHashField(
        key: String,
        field: String,
    ): Long {
        return withContext(Dispatchers.IO) {
            binaryValueCommands.hdel(key, field)
        }
    }

    suspend fun deleteListItemAtIndex(
        key: String,
        index: Long,
    ): Long {
        return withContext(Dispatchers.IO) {
            val marker = "__dioptra_delete_marker:${UUID.randomUUID()}"
                .toByteArray(StandardCharsets.UTF_8)
            binaryValueCommands.lset(key, index, marker)
            binaryValueCommands.lrem(key, 1L, marker)
        }
    }

    suspend fun deleteSetMember(
        key: String,
        member: ByteArray,
    ): Long {
        return withContext(Dispatchers.IO) {
            binaryValueCommands.srem(key, member)
        }
    }

    suspend fun deleteSortedSetMember(
        key: String,
        member: ByteArray,
    ): Long {
        return withContext(Dispatchers.IO) {
            binaryValueCommands.zrem(key, member)
        }
    }

    suspend fun deleteStreamEntry(
        key: String,
        entryId: String,
    ): Long {
        return withContext(Dispatchers.IO) {
            commands.xdel(key, entryId)
        }
    }
}
