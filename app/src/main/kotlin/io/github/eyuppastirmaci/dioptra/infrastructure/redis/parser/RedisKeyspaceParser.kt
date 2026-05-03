package io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser

class RedisKeyspaceParser {

    /**
     * Parses a Redis keyspace line such as keys=5,expires=1,avg_ttl=12345.
     */
    fun parse(
        database: String,
        rawValue: String?,
    ): RedisKeyspaceInfo {
        if (rawValue.isNullOrBlank()) {
            return RedisKeyspaceInfo.empty(database)
        }

        val properties = rawValue
            .split(",")
            .mapNotNull { part ->
                val separatorIndex = part.indexOf('=')

                if (separatorIndex == -1) {
                    null
                } else {
                    val key = part.substring(0, separatorIndex)
                    val value = part.substring(separatorIndex + 1)

                    key to value
                }
            }
            .toMap()

        return RedisKeyspaceInfo(
            database = database,
            keys = properties["keys"]?.toLongOrNull() ?: 0L,
            expires = properties["expires"]?.toLongOrNull() ?: 0L,
            averageTtl = properties["avg_ttl"]?.toLongOrNull() ?: 0L,
        )
    }
}

data class RedisKeyspaceInfo(
    val database: String,
    val keys: Long,
    val expires: Long,
    val averageTtl: Long,
) {

    companion object {

        fun empty(database: String): RedisKeyspaceInfo {
            return RedisKeyspaceInfo(
                database = database,
                keys = 0L,
                expires = 0L,
                averageTtl = 0L,
            )
        }
    }
}