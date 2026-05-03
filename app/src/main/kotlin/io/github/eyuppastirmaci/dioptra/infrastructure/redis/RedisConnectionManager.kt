package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands

class RedisConnectionManager(
    config: RedisConnectionConfig,
) : AutoCloseable {

    private val clientFactory = LettuceRedisClientFactory(config)
    private var connection: StatefulRedisConnection<String, String>? = null

    fun connect(): StatefulRedisConnection<String, String> {
        val activeConnection = connection
        if (activeConnection != null && activeConnection.isOpen) {
            return activeConnection
        }

        return clientFactory.connect().also {
            connection = it
        }
    }

    fun syncCommands(): RedisCommands<String, String> {
        return clientFactory.syncCommands(connect())
    }

    override fun close() {
        connection?.close()
        connection = null
        clientFactory.close()
    }
}
