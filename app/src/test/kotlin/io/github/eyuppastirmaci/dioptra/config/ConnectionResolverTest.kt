package io.github.eyuppastirmaci.dioptra.config

import io.github.eyuppastirmaci.dioptra.cli.CliConnectionOptions
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConnectionResolverTest {

    @Test
    fun `resolving profile carries namespace analysis settings into runtime config`() {
        val tempDir = createTempDirectory("dioptra-resolver-test")
        val store = HoconConnectionProfileStore(
            configPath = tempDir.resolve("config.conf"),
        )
        store.save(
            DioptraConfig(
                profiles = listOf(
                    RedisConnectionProfile(
                        name = "local",
                        namespaceAnalysisSettings = NamespaceAnalysisSettings(
                            delimiters = listOf("|"),
                            namespaceDepth = 3,
                            expectedNamespaces = listOf("preferences", "settings", "options"),
                            allowedKeyPatterns = listOf("bull:*"),
                            ignoredKeyPatterns = listOf("tmp:*") ,
                            allowWhitespaceInKeys = true,
                        ),
                    )
                )
            )
        )

        val resolver = ConnectionResolver(profileStore = store)

        val resolution = resolver.resolve(
            CliConnectionOptions(profile = "local"),
        )

        val ready = assertIs<ConnectionResolution.Ready>(resolution)
        assertEquals(listOf("|"), ready.config.namespaceAnalysisSettings.normalizedDelimiters)
        assertEquals(3, ready.config.namespaceAnalysisSettings.normalizedNamespaceDepth)
        assertEquals(listOf("preferences", "settings", "options"), ready.config.namespaceAnalysisSettings.normalizedExpectedNamespaces)
        assertEquals(listOf("bull:*"), ready.config.namespaceAnalysisSettings.normalizedAllowedKeyPatterns)
        assertEquals(listOf("tmp:*"), ready.config.namespaceAnalysisSettings.normalizedIgnoredKeyPatterns)
        assertEquals(true, ready.config.namespaceAnalysisSettings.allowWhitespaceInKeys)
    }
}