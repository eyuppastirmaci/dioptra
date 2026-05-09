package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceHealth
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceHealthLevel
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceRiskReason

data class NamespaceHealthInput(
    val keyCount: Long,
    val noTtlKeyCount: Long,
    val ttlCoveragePercent: Double,
    val memoryConcentrationPercent: Double,
    val unexpectedNamespace: Boolean,
    val anomalyCount: Int,
    val memoryUsageKnown: Boolean,
)

data class NamespaceHealthAssessment(
    val health: NamespaceHealth,
    val riskReasons: List<NamespaceRiskReason>,
)

class NamespaceHealthScorer {

    fun assess(input: NamespaceHealthInput): NamespaceHealthAssessment {
        val penalties = buildList {
            if (input.unexpectedNamespace) {
                add(
                    Penalty(
                        reason = NamespaceRiskReason.UNEXPECTED_NAMESPACE,
                        points = 25,
                        message = "Namespace was not expected by the grouping rules",
                    )
                )
            }

            val memoryPenalty = classifyMemoryConcentration(input.memoryConcentrationPercent)
            if (memoryPenalty != null) {
                add(memoryPenalty)
            }

            val noTtlPenalty = classifyNoTtlRatio(
                keyCount = input.keyCount,
                noTtlKeyCount = input.noTtlKeyCount,
            )
            if (noTtlPenalty != null) {
                add(noTtlPenalty)
            }

            val ttlCoveragePenalty = classifyTtlCoverage(input.ttlCoveragePercent)
            if (ttlCoveragePenalty != null) {
                add(ttlCoveragePenalty)
            }

            val namespaceSizePenalty = classifyNamespaceSize(input.keyCount)
            if (namespaceSizePenalty != null) {
                add(namespaceSizePenalty)
            }

            if (input.anomalyCount > 0) {
                add(
                    Penalty(
                        reason = NamespaceRiskReason.NAMING_ANOMALY,
                        points = if (input.anomalyCount >= 10) 15 else 8,
                        message = "Detected ${input.anomalyCount} naming anomalies",
                    )
                )
            }

            if (!input.memoryUsageKnown) {
                add(
                    Penalty(
                        reason = NamespaceRiskReason.UNKNOWN_MEMORY_USAGE,
                        points = 10,
                        message = "Memory usage is partially or fully unknown",
                    )
                )
            }
        }

        val orderedPenalties = penalties.sortedWith(
            compareByDescending<Penalty> { it.points }
                .thenBy { it.reason.ordinal }
        )

        val score = (MAX_HEALTH_SCORE - orderedPenalties.sumOf { it.points })
            .coerceIn(MIN_HEALTH_SCORE, MAX_HEALTH_SCORE)
        val level = scoreToLevel(score)
        val primaryReason = orderedPenalties.firstOrNull()?.message ?: HEALTHY_MESSAGE

        return NamespaceHealthAssessment(
            health = NamespaceHealth(
                score = score,
                level = level,
                primaryReason = primaryReason,
            ),
            riskReasons = orderedPenalties.map { it.reason }.distinct(),
        )
    }

    private fun classifyNoTtlRatio(
        keyCount: Long,
        noTtlKeyCount: Long,
    ): Penalty? {
        if (keyCount <= 0L || noTtlKeyCount <= 0L) {
            return null
        }

        val ratio = noTtlKeyCount.toDouble() / keyCount.toDouble() * 100.0
        return when {
            ratio >= 90.0 -> Penalty(
                reason = NamespaceRiskReason.HIGH_NO_TTL_RATIO,
                points = 30,
                message = "Nearly all keys are missing TTL (${ratio.toInt()}%)",
            )
            ratio >= 50.0 -> Penalty(
                reason = NamespaceRiskReason.HIGH_NO_TTL_RATIO,
                points = 18,
                message = "A high share of keys are missing TTL (${ratio.toInt()}%)",
            )
            ratio >= 20.0 -> Penalty(
                reason = NamespaceRiskReason.HIGH_NO_TTL_RATIO,
                points = 8,
                message = "Some keys are missing TTL (${ratio.toInt()}%)",
            )
            else -> null
        }
    }

    private fun classifyTtlCoverage(coveragePercent: Double): Penalty? {
        return when {
            coveragePercent < 25.0 -> Penalty(
                reason = NamespaceRiskReason.LOW_TTL_COVERAGE,
                points = 20,
                message = "TTL coverage is very low (${coveragePercent.toInt()}%)",
            )
            coveragePercent < 50.0 -> Penalty(
                reason = NamespaceRiskReason.LOW_TTL_COVERAGE,
                points = 10,
                message = "TTL coverage is low (${coveragePercent.toInt()}%)",
            )
            else -> null
        }
    }

    private fun classifyMemoryConcentration(memoryConcentrationPercent: Double): Penalty? {
        return when {
            memoryConcentrationPercent >= 50.0 -> Penalty(
                reason = NamespaceRiskReason.HIGH_MEMORY_CONCENTRATION,
                points = 30,
                message = "Namespace owns at least half of analyzed memory",
            )
            memoryConcentrationPercent >= 25.0 -> Penalty(
                reason = NamespaceRiskReason.HIGH_MEMORY_CONCENTRATION,
                points = 15,
                message = "Namespace is a major memory consumer",
            )
            else -> null
        }
    }

    private fun classifyNamespaceSize(keyCount: Long): Penalty? {
        return when {
            keyCount >= 100_000L -> Penalty(
                reason = NamespaceRiskReason.LARGE_NAMESPACE,
                points = 20,
                message = "Namespace contains at least 100k keys",
            )
            keyCount >= 10_000L -> Penalty(
                reason = NamespaceRiskReason.LARGE_NAMESPACE,
                points = 10,
                message = "Namespace contains at least 10k keys",
            )
            else -> null
        }
    }

    private fun scoreToLevel(score: Int): NamespaceHealthLevel {
        return when {
            score >= 80 -> NamespaceHealthLevel.HEALTHY
            score >= 60 -> NamespaceHealthLevel.WATCH
            score >= 30 -> NamespaceHealthLevel.RISKY
            else -> NamespaceHealthLevel.CRITICAL
        }
    }

    private data class Penalty(
        val reason: NamespaceRiskReason,
        val points: Int,
        val message: String,
    )

    private companion object {
        const val MIN_HEALTH_SCORE = 0
        const val MAX_HEALTH_SCORE = 100
        const val HEALTHY_MESSAGE = "No namespace risk signals detected"
    }
}