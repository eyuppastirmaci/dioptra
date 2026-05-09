package io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser

import io.github.eyuppastirmaci.dioptra.domain.commandstats.CommandStat
import org.slf4j.LoggerFactory

/**
 * Parses the raw text of `INFO commandstats` into typed [CommandStat] objects.
 *
 * Expected format per line:
 *   cmdstat_<command>:calls=N,usec=N,usec_per_call=N.NN,rejected_calls=N,failed_calls=N
 *
 * Lines that do not match this format (e.g. the `# Commandstats` section header or blank
 * lines) are silently skipped. Individual field parse errors are logged and the stat is
 * still included with a zero for the unparseable field rather than dropping the whole entry.
 */
class RedisCommandStatsParser {

    private val logger = LoggerFactory.getLogger(RedisCommandStatsParser::class.java)

    fun parse(raw: String): List<CommandStat> {
        return raw.lines()
            .filter { it.startsWith("cmdstat_") }
            .mapNotNull { parseLine(it.trim()) }
    }

    private fun parseLine(line: String): CommandStat? {
        // "cmdstat_get:calls=1000,usec=50000,usec_per_call=50.00,rejected_calls=0,failed_calls=0"
        val colonIndex = line.indexOf(':')
        if (colonIndex < 0) return null

        val commandName = line.substring("cmdstat_".length, colonIndex).uppercase()
        val fields = parseFields(line.substring(colonIndex + 1))

        return CommandStat(
            command = commandName,
            calls = fields.long("calls"),
            totalUsec = fields.long("usec"),
            usecPerCall = fields.double("usec_per_call"),
            rejectedCalls = fields.long("rejected_calls"),
            failedCalls = fields.long("failed_calls"),
        )
    }

    // Returns a map of key → raw-string-value from "k1=v1,k2=v2,..." notation
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
        fun long(key: String): Long {
            return map[key]?.toLongOrNull() ?: run {
                logger.debug("commandstats field '{}' missing or not a long; defaulting to 0", key)
                0L
            }
        }

        fun double(key: String): Double {
            return map[key]?.toDoubleOrNull() ?: run {
                logger.debug("commandstats field '{}' missing or not a double; defaulting to 0.0", key)
                0.0
            }
        }
    }
}
