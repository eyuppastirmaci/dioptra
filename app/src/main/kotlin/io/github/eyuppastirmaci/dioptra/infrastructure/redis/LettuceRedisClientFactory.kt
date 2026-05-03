package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands

class LettuceRedisClientFactory(
    config: RedisConnectionConfig,
) : AutoCloseable {

    private val client: RedisClient = RedisClient.create(config.uri)

    fun connect(): StatefulRedisConnection<String, String> {
        return client.connect()
    }

    fun syncCommands(
        connection: StatefulRedisConnection<String, String>,
    ): RedisCommands<String, String> {
        return connection.sync()
    }

    override fun close() {
        client.shutdown()
    }
}