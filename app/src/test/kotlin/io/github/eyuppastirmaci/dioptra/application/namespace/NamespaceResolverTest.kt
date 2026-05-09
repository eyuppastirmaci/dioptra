package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NamespaceResolverTest {

    @Test
    fun `resolves first segment by default`() {
        val resolver = NamespaceResolver()

        val identity = resolver.resolve("user:123:profile")

        assertEquals("user", identity.displayName)
        assertEquals("user", identity.normalizedName)
        assertEquals(1, identity.depth)
        assertNull(identity.matcherRule)
    }

    @Test
    fun `resolves custom depth from matching rule`() {
        val resolver = NamespaceResolver(
            rules = listOf(
                NamespaceResolutionRule(
                    pattern = "user:*",
                    depth = 2,
                )
            )
        )

        val identity = resolver.resolve("user:123:profile")

        assertEquals("user:123", identity.displayName)
        assertEquals("user:123", identity.normalizedName)
        assertEquals(2, identity.depth)
        assertEquals("user:*", identity.matcherRule)
    }

    @Test
    fun `uses explicit namespace name when provided by rule`() {
        val resolver = NamespaceResolver(
            rules = listOf(
                NamespaceResolutionRule(
                    pattern = "cache:*",
                    namespaceName = "cache-hot",
                )
            )
        )

        val identity = resolver.resolve("cache:prompt:1")

        assertEquals("cache-hot", identity.displayName)
        assertEquals("cache-hot", identity.normalizedName)
        assertEquals(1, identity.depth)
        assertEquals("cache:*", identity.matcherRule)
    }

    @Test
    fun `resolves namespace using configured delimiter and depth`() {
        val resolver = NamespaceResolver(
            settings = NamespaceAnalysisSettings(
                delimiters = listOf("|"),
                namespaceDepth = 2,
            )
        )

        val identity = resolver.resolve("preferences|settings|ui")

        assertEquals("preferences|settings", identity.displayName)
        assertEquals("preferences|settings", identity.normalizedName)
        assertEquals(2, identity.depth)
    }

    @Test
    fun `marks namespace as unexpected when outside configured expectations`() {
        val resolver = NamespaceResolver(
            settings = NamespaceAnalysisSettings(
                expectedNamespaces = listOf("preferences", "settings"),
            )
        )

        val identity = resolver.resolve("options:theme")

        assertTrue(resolver.isUnexpected(identity))
    }
}