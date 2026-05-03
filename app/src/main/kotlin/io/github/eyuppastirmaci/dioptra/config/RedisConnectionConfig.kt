package io.github.eyuppastirmaci.dioptra.config

data class RedisConnectionConfig(
    val host: String = "localhost",
    val port: Int = 6379,
) {
    val uri: String
        get() = "redis://$host:$port"
}