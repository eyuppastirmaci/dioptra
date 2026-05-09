package io.github.eyuppastirmaci.dioptra.config

import kotlin.test.Test
import kotlin.test.assertEquals

class NamespaceAnalysisSettingsTest {

    @Test
    fun `normalizes delimiters expected namespaces and key patterns`() {
        val settings = NamespaceAnalysisSettings(
            delimiters = listOf(" : ", "|", "", ":"),
            expectedNamespaces = listOf(" user ", "session", "", "user"),
            allowedKeyPatterns = listOf(" bull:* ", "tenant:*:lock:*", "bull:*"),
            ignoredKeyPatterns = listOf(" tmp:* ", "", "tmp:*", "__redis__:*"),
        )

        assertEquals(listOf(":", "|", ":"), settings.normalizedDelimiters)
        assertEquals(listOf("user", "session"), settings.normalizedExpectedNamespaces)
        assertEquals(listOf("bull:*", "tenant:*:lock:*"), settings.normalizedAllowedKeyPatterns)
        assertEquals(listOf("tmp:*", "__redis__:*"), settings.normalizedIgnoredKeyPatterns)
    }

    @Test
    fun `falls back to safe defaults when delimiter list or depth is invalid`() {
        val settings = NamespaceAnalysisSettings(
            delimiters = listOf(" ", ""),
            namespaceDepth = 0,
        )

        assertEquals(listOf(":"), settings.normalizedDelimiters)
        assertEquals(1, settings.normalizedNamespaceDepth)
    }
}