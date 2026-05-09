package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceHealthLevel
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceRiskReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NamespaceHealthScorerTest {

    private val scorer = NamespaceHealthScorer()

    @Test
    fun `returns healthy assessment for low risk namespace`() {
        val assessment = scorer.assess(
            NamespaceHealthInput(
                keyCount = 20,
                noTtlKeyCount = 0,
                ttlCoveragePercent = 100.0,
                memoryConcentrationPercent = 2.0,
                unexpectedNamespace = false,
                anomalyCount = 0,
                memoryUsageKnown = true,
            )
        )

        assertEquals(NamespaceHealthLevel.HEALTHY, assessment.health.level)
        assertEquals(100, assessment.health.score)
        assertTrue(assessment.riskReasons.isEmpty())
    }

    @Test
    fun `returns critical assessment for multiple strong risk signals`() {
        val assessment = scorer.assess(
            NamespaceHealthInput(
                keyCount = 150_000,
                noTtlKeyCount = 145_000,
                ttlCoveragePercent = 10.0,
                memoryConcentrationPercent = 60.0,
                unexpectedNamespace = true,
                anomalyCount = 12,
                memoryUsageKnown = false,
            )
        )

        assertEquals(NamespaceHealthLevel.CRITICAL, assessment.health.level)
        assertTrue(NamespaceRiskReason.UNEXPECTED_NAMESPACE in assessment.riskReasons)
        assertTrue(NamespaceRiskReason.HIGH_NO_TTL_RATIO in assessment.riskReasons)
        assertTrue(NamespaceRiskReason.HIGH_MEMORY_CONCENTRATION in assessment.riskReasons)
        assertTrue(NamespaceRiskReason.LARGE_NAMESPACE in assessment.riskReasons)
        assertTrue(NamespaceRiskReason.LOW_TTL_COVERAGE in assessment.riskReasons)
        assertTrue(NamespaceRiskReason.NAMING_ANOMALY in assessment.riskReasons)
        assertTrue(NamespaceRiskReason.UNKNOWN_MEMORY_USAGE in assessment.riskReasons)
    }
}