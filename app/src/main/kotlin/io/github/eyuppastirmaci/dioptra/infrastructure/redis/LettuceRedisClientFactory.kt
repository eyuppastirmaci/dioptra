package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import java.time.Duration

class LettuceRedisClientFactory(
    private val config: RedisConnectionConfig,
) : AutoCloseable {

    private val client: RedisClient = RedisClient.create(buildRedisUri())

    fun connect(): StatefulRedisConnection<String, String> {
        return client.connect()
    }

    fun connectBinaryValues(): StatefulRedisConnection<String, ByteArray> {
        return client.connect(
            RedisCodec.of(
                StringCodec.UTF8,
                ByteArrayCodec.INSTANCE,
            )
        )
    }

    fun syncCommands(
        connection: StatefulRedisConnection<String, String>,
    ): RedisCommands<String, String> {
        return connection.sync()
    }

    fun syncBinaryValueCommands(
        connection: StatefulRedisConnection<String, ByteArray>,
    ): RedisCommands<String, ByteArray> {
        return connection.sync()
    }

    override fun close() {
        client.shutdown()
    }

    private fun buildRedisUri(): RedisURI {
        val builder = RedisURI.Builder
            .redis(config.host, config.port)
            .withDatabase(config.database)
            .withSsl(config.tls)
            .withTimeout(Duration.ofMillis(config.timeoutMillis))

        val password = config.password
        val username = config.username

        when {
            !username.isNullOrBlank() && !password.isNullOrEmpty() -> {
                builder.withAuthentication(username, password.toCharArray())
            }

            !password.isNullOrEmpty() -> {
                builder.withPassword(password.toCharArray())
            }
        }

        return builder.build()
    }
}
