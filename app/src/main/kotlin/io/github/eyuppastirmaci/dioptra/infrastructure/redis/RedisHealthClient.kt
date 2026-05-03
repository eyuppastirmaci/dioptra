package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.lettuce.core.api.sync.RedisCommands

class RedisHealthClient(
    private val commands: RedisCommands<String, String>,
) {
    fun ping(): String {
        return commands.ping()
    }
}