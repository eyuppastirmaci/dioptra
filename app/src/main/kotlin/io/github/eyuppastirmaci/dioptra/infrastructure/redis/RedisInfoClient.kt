package io.github.eyuppastirmaci.dioptra.infrastructure.redis

import io.lettuce.core.api.sync.RedisCommands

class RedisInfoClient(
    private val commands: RedisCommands<String, String>,
) {
    fun fetchServerInfo(): String {
        return commands.info()
    }
}