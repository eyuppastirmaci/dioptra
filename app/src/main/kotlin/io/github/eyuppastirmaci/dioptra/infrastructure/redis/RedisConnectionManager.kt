package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands

class RedisConnectionManager(
    val config: RedisConnectionConfig,
) : AutoCloseable {

    private val clientFactory = LettuceRedisClientFactory(config)
    private var connection: StatefulRedisConnection<String, String>? = null
    private var binaryValueConnection: StatefulRedisConnection<String, ByteArray>? = null

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

    fun connectBinaryValues(): StatefulRedisConnection<String, ByteArray> {
        val activeConnection = binaryValueConnection
        if (activeConnection != null && activeConnection.isOpen) {
            return activeConnection
        }

        return clientFactory.connectBinaryValues().also {
            binaryValueConnection = it
        }
    }

    fun syncBinaryValueCommands(): RedisCommands<String, ByteArray> {
        return clientFactory.syncBinaryValueCommands(connectBinaryValues())
    }

    override fun close() {
        connection?.close()
        binaryValueConnection?.close()
        connection = null
        binaryValueConnection = null
        clientFactory.close()
    }
}
