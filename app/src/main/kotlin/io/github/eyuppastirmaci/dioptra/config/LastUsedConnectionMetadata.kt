package io.github.eyuppastirmaci.dioptra.config

import java.time.Instant

data class LastUsedConnectionMetadata(
    val profileName: String? = null,
    val host: String = "localhost",
    val port: Int = 6379,
    val database: Int = 0,
    val username: String? = null,
    val tls: Boolean = false,
    val lastConnectedAt: Instant = Instant.now(),
) {

    companion object {
        fun from(config: RedisConnectionConfig): LastUsedConnectionMetadata {
            return LastUsedConnectionMetadata(
                profileName = config.name.takeUnless { it == "local" || it == "cli-url" },
                host = config.host,
                port = config.port,
                database = config.database,
                username = config.username,
                tls = config.tls,
            )
        }
    }
}
