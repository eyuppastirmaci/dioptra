package io.github.eyuppastirmaci.dioptra.application.slowlog

import io.github.eyuppastirmaci.dioptra.domain.slowlog.RedisSlowlogEntry
import io.github.eyuppastirmaci.dioptra.domain.slowlog.RedisSlowlogSnapshot
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisSlowlogClient

class LoadSlowlogUseCase(
    private val redisSlowlogClient: RedisSlowlogClient,
) {

    fun load(count: Long = 128L): RedisSlowlogSnapshot {
        val totalEntries = redisSlowlogClient.fetchLength()
        val rawEntries = redisSlowlogClient.fetchEntries(count.toInt())

        val entries = rawEntries.map { raw ->
            RedisSlowlogEntry(
                id = raw.id,
                timestampSeconds = raw.timestampSeconds,
                durationMicroseconds = raw.durationMicroseconds,
                command = raw.command,
                arguments = raw.arguments,
                clientAddress = raw.clientAddress,
                clientName = raw.clientName,
            )
        }

        return RedisSlowlogSnapshot(
            totalEntries = totalEntries,
            entries = entries,
        )
    }
}
