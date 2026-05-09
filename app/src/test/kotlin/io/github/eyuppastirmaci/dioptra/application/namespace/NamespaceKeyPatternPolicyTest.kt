package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NamespaceKeyPatternPolicyTest {

    @Test
    fun `matches exact prefix and wildcard patterns`() {
        val policy = NamespaceKeyPatternPolicy(
            NamespaceAnalysisSettings(
                allowedKeyPatterns = listOf("bull:*", "tenant:", "healthcheck"),
            )
        )

        assertTrue(policy.isAllowed("bull:jobs:1"))
        assertTrue(policy.isAllowed("tenant:user:5"))
        assertTrue(policy.isAllowed("healthcheck"))
        assertFalse(policy.isAllowed("other:key"))
    }

    @Test
    fun `ignored patterns take independent matches`() {
        val policy = NamespaceKeyPatternPolicy(
            NamespaceAnalysisSettings(
                ignoredKeyPatterns = listOf("__redis__:*", "tmp:"),
            )
        )

        assertTrue(policy.isIgnored("__redis__:stats"))
        assertTrue(policy.isIgnored("tmp:job:1"))
        assertFalse(policy.isIgnored("tenant:tmp"))
    }

    @Test
    fun `match all wildcard applies to every key`() {
        val policy = NamespaceKeyPatternPolicy(
            NamespaceAnalysisSettings(
                allowedKeyPatterns = listOf("*"),
                ignoredKeyPatterns = listOf("*"),
            )
        )

        assertEquals(true, policy.isAllowed("anything:goes"))
        assertEquals(true, policy.isIgnored("anything:goes"))
    }
}