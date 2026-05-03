package io.github.eyuppastirmaci.dioptra.config

sealed interface ConnectionResolution {

    data class Ready(
        val config: RedisConnectionConfig,
        val source: ConnectionSource,
    ) : ConnectionResolution

    data class NeedsUserInput(
        val reason: String,
        val partialConfig: RedisConnectionConfig? = null,
    ) : ConnectionResolution
}

enum class ConnectionSource {
    CliUrl,
    CliProfile,
    CliOptions,
    DefaultProfile,
}
