package io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser

import io.github.eyuppastirmaci.dioptra.domain.latency.LatencyStat
import org.slf4j.LoggerFactory

/**
 * Parses the raw text of `INFO latencystats` into typed [LatencyStat] objects.
 *
 * Expected format per line (Redis 7.0+):
 *   latency_percentiles_usec_<command>:p50=N.NNN,p99=N.NNN,p99.9=N.NNN
 *
 * Lines not matching this pattern (section headers, blank lines) are silently skipped.
 * Missing percentile fields default to 0.0 with a debug log.
 *
 * If the section is empty (Redis < 7.0 or latency-tracking disabled), an empty list
 * is returned — the screen handles this gracefully.
 */
class RedisLatencyStatsParser {

    private val logger = LoggerFactory.getLogger(RedisLatencyStatsParser::class.java)

    private val PREFIX = "latency_percentiles_usec_"

    fun parse(raw: String): List<LatencyStat> {
        return raw.lines()
            .filter { it.startsWith(PREFIX) }
            .mapNotNull { parseLine(it.trim()) }
    }

    private fun parseLine(line: String): LatencyStat? {
        val colonIndex = line.indexOf(':')
        if (colonIndex < 0) return null

        val commandName = line.substring(PREFIX.length, colonIndex).uppercase()
        val fields = parseFields(line.substring(colonIndex + 1))

        return LatencyStat(
            command = commandName,
            p50Usec = fields.double("p50"),
            p99Usec = fields.double("p99"),
            p999Usec = fields.double("p99.9"),
        )
    }

    private fun parseFields(segment: String): FieldMap {
        val map = mutableMapOf<String, String>()
        segment.split(',').forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                map[pair.substring(0, eq).trim()] = pair.substring(eq + 1).trim()
            }
        }
        return FieldMap(map, logger)
    }

    private class FieldMap(
        private val map: Map<String, String>,
        private val logger: org.slf4j.Logger,
    ) {
        fun double(key: String): Double {
            return map[key]?.toDoubleOrNull() ?: run {
                logger.debug("latencystats field '{}' missing or not a double; defaulting to 0.0", key)
                0.0
            }
        }
    }
}
