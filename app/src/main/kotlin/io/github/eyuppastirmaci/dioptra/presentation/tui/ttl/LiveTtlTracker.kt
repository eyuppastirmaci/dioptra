package io.github.eyuppastirmaci.dioptra.presentation.tui.ttl

import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeySummary
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus

data class LiveTtlDisplay(
    val text: String,
    val expired: Boolean = false,
)

class LiveTtlTracker(
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private val observations = mutableMapOf<String, TtlObservation>()

    @Synchronized
    fun observe(
        keyName: String,
        ttl: RedisKeyTtlStatus,
    ) {
        if (ttl is RedisKeyTtlStatus.Expiring) {
            observations[keyName] = TtlObservation(
                ttlSeconds = ttl.seconds,
                observedAtNanos = nowNanos(),
            )
        } else {
            observations.remove(keyName)
        }
    }

    @Synchronized
    fun observeAll(keys: List<RedisKeySummary>) {
        val names = keys.mapTo(mutableSetOf()) { it.name }
        observations.keys.retainAll(names)
        keys.forEach { key -> observe(key.name, key.ttl) }
    }

    @Synchronized
    fun forget(keyName: String) {
        observations.remove(keyName)
    }

    @Synchronized
    fun forgetAll(keyNames: Collection<String>) {
        keyNames.forEach(observations::remove)
    }

    @Synchronized
    fun display(
        keyName: String,
        ttl: RedisKeyTtlStatus,
    ): LiveTtlDisplay {
        return when (ttl) {
            RedisKeyTtlStatus.KeyDoesNotExist -> LiveTtlDisplay("missing")
            RedisKeyTtlStatus.NoExpiration -> LiveTtlDisplay("! no ttl")
            is RedisKeyTtlStatus.Unknown -> LiveTtlDisplay("unknown(${ttl.rawValue})")
            is RedisKeyTtlStatus.Expiring -> expiringDisplay(keyName, ttl.seconds)
        }
    }

    @Synchronized
    fun expiredGraceComplete(
        keyName: String,
        ttl: RedisKeyTtlStatus,
        graceMillis: Long,
    ): Boolean {
        if (ttl !is RedisKeyTtlStatus.Expiring) {
            return false
        }

        display(keyName, ttl)
        val expiredAtNanos = observations[keyName]?.expiredAtNanos ?: return false
        return nowNanos() - expiredAtNanos >= graceMillis * NANOS_PER_MILLISECOND
    }

    private fun expiringDisplay(
        keyName: String,
        ttlSeconds: Long,
    ): LiveTtlDisplay {
        val now = nowNanos()
        val observation = observations.getOrPut(keyName) {
            TtlObservation(
                ttlSeconds = ttlSeconds,
                observedAtNanos = now,
            )
        }
        val effectiveObservation = if (observation.ttlSeconds == ttlSeconds) {
            observation
        } else {
            TtlObservation(
                ttlSeconds = ttlSeconds,
                observedAtNanos = now,
            ).also { observations[keyName] = it }
        }

        val remainingNanos = safeSecondsToNanos(effectiveObservation.ttlSeconds) -
            (now - effectiveObservation.observedAtNanos)

        if (remainingNanos <= 0L) {
            if (effectiveObservation.expiredAtNanos == null) {
                observations[keyName] = effectiveObservation.copy(expiredAtNanos = now)
            }
            return LiveTtlDisplay(text = "EXPIRED", expired = true)
        }

        val remainingSeconds = ceilDiv(remainingNanos, NANOS_PER_SECOND)
        return LiveTtlDisplay(text = formatRemaining(remainingSeconds))
    }

    private fun formatRemaining(seconds: Long): String {
        return when {
            seconds >= SECONDS_PER_DAY -> plural(ceilDiv(seconds, SECONDS_PER_DAY), "day")
            seconds >= SECONDS_PER_HOUR -> plural(ceilDiv(seconds, SECONDS_PER_HOUR), "hour")
            seconds >= SECONDS_PER_MINUTE -> plural(ceilDiv(seconds, SECONDS_PER_MINUTE), "minute")
            else -> plural(seconds, "second")
        }
    }

    private fun plural(
        value: Long,
        unit: String,
    ): String {
        val suffix = if (value == 1L) unit else "${unit}s"
        return "$value $suffix"
    }

    private fun safeSecondsToNanos(seconds: Long): Long {
        if (seconds <= 0L) {
            return 0L
        }
        if (seconds > Long.MAX_VALUE / NANOS_PER_SECOND) {
            return Long.MAX_VALUE
        }
        return seconds * NANOS_PER_SECOND
    }

    private fun ceilDiv(
        value: Long,
        divisor: Long,
    ): Long {
        return ((value - 1L) / divisor) + 1L
    }

    private data class TtlObservation(
        val ttlSeconds: Long,
        val observedAtNanos: Long,
        val expiredAtNanos: Long? = null,
    )

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
        const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR
    }
}
