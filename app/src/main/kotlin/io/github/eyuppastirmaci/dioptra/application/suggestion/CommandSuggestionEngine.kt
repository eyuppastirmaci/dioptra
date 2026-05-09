package io.github.eyuppastirmaci.dioptra.application.suggestion

import io.github.eyuppastirmaci.dioptra.application.format.ByteSizeFormatter
import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyType
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskAnalysisSnapshot
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskKeyFinding
import io.github.eyuppastirmaci.dioptra.domain.risk.RiskReason
import io.github.eyuppastirmaci.dioptra.domain.suggestion.CommandSuggestion
import io.github.eyuppastirmaci.dioptra.domain.suggestion.SuggestionCategory

class CommandSuggestionEngine(
    private val byteSizeFormatter: ByteSizeFormatter,
) {

    fun generate(
        dashboard: RedisDashboardSnapshot,
        riskAnalysis: RiskAnalysisSnapshot? = null,
    ): List<CommandSuggestion> = buildList {
        addAll(memorysuggestions(dashboard))
        addAll(clientSuggestions(dashboard))
        addAll(persistenceSuggestions(dashboard))
        addAll(replicationSuggestions(dashboard))
        addAll(performanceSuggestions())
        addAll(keyspaceSuggestions())
        if (riskAnalysis != null) {
            addAll(riskSuggestions(riskAnalysis))
        }
    }

    private fun memorysuggestions(dashboard: RedisDashboardSnapshot): List<CommandSuggestion> = buildList {
        add(
            CommandSuggestion(
                category = SuggestionCategory.MEMORY,
                title = "Inspect memory usage",
                commands = listOf("INFO memory", "MEMORY STATS"),
            )
        )

        if (!dashboard.memoryFragmentationHealthy) {
            add(
                CommandSuggestion(
                    category = SuggestionCategory.MEMORY,
                    title = "Fix memory fragmentation",
                    reason = dashboard.memoryFragmentationHint,
                    commands = listOf("MEMORY DOCTOR", "MEMORY PURGE"),
                )
            )
        }

        if (dashboard.evictedKeys > 0) {
            add(
                CommandSuggestion(
                    category = SuggestionCategory.MEMORY,
                    title = "Review eviction policy",
                    reason = "${dashboard.evictedKeys} keys evicted",
                    commands = listOf("CONFIG GET maxmemory-policy", "CONFIG GET maxmemory"),
                )
            )
        }
    }

    private fun clientSuggestions(dashboard: RedisDashboardSnapshot): List<CommandSuggestion> = buildList {
        add(
            CommandSuggestion(
                category = SuggestionCategory.CLIENTS,
                title = "List active clients",
                commands = listOf("CLIENT INFO", "CLIENT LIST"),
            )
        )

        if (dashboard.blockedClients > 0) {
            add(
                CommandSuggestion(
                    category = SuggestionCategory.CLIENTS,
                    title = "Diagnose blocked clients",
                    reason = "${dashboard.blockedClients} blocked clients",
                    commands = listOf("CLIENT LIST", "CLIENT LIST type blocked"),
                )
            )
        }
    }

    private fun persistenceSuggestions(dashboard: RedisDashboardSnapshot): List<CommandSuggestion> = buildList {
        add(
            CommandSuggestion(
                category = SuggestionCategory.PERSISTENCE,
                title = "Check persistence status",
                commands = listOf("INFO persistence"),
            )
        )

        if (!dashboard.rdbStatusHealthy) {
            add(
                CommandSuggestion(
                    category = SuggestionCategory.PERSISTENCE,
                    title = "Trigger RDB snapshot",
                    reason = "RDB status: ${dashboard.rdbStatus}",
                    commands = listOf("BGSAVE", "LASTSAVE"),
                )
            )
        }

        if (dashboard.aofEnabled && !dashboard.aofStatusHealthy) {
            add(
                CommandSuggestion(
                    category = SuggestionCategory.PERSISTENCE,
                    title = "Rewrite AOF file",
                    reason = "AOF status: ${dashboard.aofStatus}",
                    commands = listOf("BGREWRITEAOF", "INFO persistence"),
                )
            )
        }
    }

    private fun replicationSuggestions(dashboard: RedisDashboardSnapshot): List<CommandSuggestion> = buildList {
        add(
            CommandSuggestion(
                category = SuggestionCategory.REPLICATION,
                title = "Check replication health",
                commands = listOf("INFO replication"),
            )
        )

        if (dashboard.replicationRole == "slave" && !dashboard.masterLinkHealthy) {
            add(
                CommandSuggestion(
                    category = SuggestionCategory.REPLICATION,
                    title = "Debug replication link",
                    reason = "Master link status: ${dashboard.masterLinkStatus}",
                    commands = listOf("INFO replication", "REPLICAOF NO ONE"),
                )
            )
        }
    }

    private fun performanceSuggestions(): List<CommandSuggestion> = listOf(
        CommandSuggestion(
            category = SuggestionCategory.PERFORMANCE,
            title = "Review slow commands",
            commands = listOf("SLOWLOG GET 25", "SLOWLOG LEN", "SLOWLOG RESET"),
        ),
        CommandSuggestion(
            category = SuggestionCategory.PERFORMANCE,
            title = "Check latency events",
            commands = listOf("LATENCY LATEST", "LATENCY HISTORY event"),
        ),
        CommandSuggestion(
            category = SuggestionCategory.PERFORMANCE,
            title = "Tune slowlog threshold",
            commands = listOf(
                "CONFIG GET slowlog-log-slower-than",
                "CONFIG SET slowlog-log-slower-than 1000",
            ),
        ),
        CommandSuggestion(
            category = SuggestionCategory.PERFORMANCE,
            title = "Inspect command statistics",
            commands = listOf("INFO commandstats"),
        ),
    )

    private fun keyspaceSuggestions(): List<CommandSuggestion> = listOf(
        CommandSuggestion(
            category = SuggestionCategory.KEYSPACE,
            title = "Count keys in all databases",
            commands = listOf("DBSIZE", "INFO keyspace"),
        ),
        CommandSuggestion(
            category = SuggestionCategory.KEYSPACE,
            title = "Scan keys with a pattern",
            commands = listOf("SCAN 0 COUNT 100 MATCH *", "SCAN 0 COUNT 100 MATCH prefix:*"),
        ),
        CommandSuggestion(
            category = SuggestionCategory.KEYSPACE,
            title = "Inspect a specific key",
            commands = listOf("TYPE <key>", "TTL <key>", "MEMORY USAGE <key>"),
        ),
    )

    private fun riskSuggestions(snapshot: RiskAnalysisSnapshot): List<CommandSuggestion> = buildList {
        snapshot.topLargestKeys.take(TOP_KEY_LIMIT).forEach { key ->
            add(bigKeySuggestion(key))
        }

        snapshot.topNoTtlKeys.take(TOP_KEY_LIMIT).forEach { key ->
            add(noTtlKeySuggestion(key))
        }

        snapshot.riskyPatterns.take(TOP_PATTERN_LIMIT).forEach { pattern ->
            add(
                CommandSuggestion(
                    category = SuggestionCategory.RISK,
                    title = "Scan risky pattern",
                    reason = "${pattern.keyCount} keys, risk: ${pattern.riskLevel.name.lowercase()}",
                    commands = listOf(
                        "SCAN 0 COUNT 100 MATCH ${pattern.patternName}*",
                        "SCAN 0 COUNT 100 MATCH ${pattern.patternName}* TYPE string",
                    ),
                )
            )
        }
    }

    private fun bigKeySuggestion(key: RiskKeyFinding): CommandSuggestion {
        val memoryHint = when (val m = key.memoryUsage) {
            RedisKeyMemoryUsage.Unknown -> ""
            is RedisKeyMemoryUsage.Known -> " (${byteSizeFormatter.format(m.bytes)})"
        }
        val commands = buildList {
            add("MEMORY USAGE ${key.name}")
            add("TYPE ${key.name}")
            add("OBJECT ENCODING ${key.name}")
            when (key.type) {
                RedisKeyType.HASH -> add("HLEN ${key.name}")
                RedisKeyType.LIST -> add("LLEN ${key.name}")
                RedisKeyType.SET -> add("SCARD ${key.name}")
                RedisKeyType.ZSET -> add("ZCARD ${key.name}")
                RedisKeyType.STREAM -> add("XLEN ${key.name}")
                else -> Unit
            }
        }
        return CommandSuggestion(
            category = SuggestionCategory.RISK,
            title = "Inspect big key",
            reason = "${key.name}$memoryHint",
            commands = commands,
        )
    }

    private fun noTtlKeySuggestion(key: RiskKeyFinding): CommandSuggestion {
        val ttlHint = when (val t = key.ttl) {
            RedisKeyTtlStatus.NoExpiration -> "no TTL set"
            is RedisKeyTtlStatus.Expiring -> "TTL: ${t.seconds}s"
            else -> ""
        }
        return CommandSuggestion(
            category = SuggestionCategory.RISK,
            title = "Set TTL on persistent key",
            reason = "${key.name}${if (ttlHint.isNotEmpty()) " — $ttlHint" else ""}",
            commands = listOf(
                "TTL ${key.name}",
                "EXPIRE ${key.name} 86400",
            ),
        )
    }

    private companion object {
        const val TOP_KEY_LIMIT = 5
        const val TOP_PATTERN_LIMIT = 3
    }
}
