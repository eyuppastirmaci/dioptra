package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.config.DioptraConfig
import io.github.eyuppastirmaci.dioptra.config.HoconConnectionProfileStore
import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionProfile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SaveNamespaceAnalysisSettingsUseCaseTest {

    @Test
    fun `save persists namespace analysis settings for the active profile`() {
        val tempDir = createTempDirectory("dioptra-namespace-settings-save-test")
        val store = HoconConnectionProfileStore(
            configPath = tempDir.resolve("config.conf"),
        )
        store.save(
            DioptraConfig(
                profiles = listOf(
                    RedisConnectionProfile(
                        name = "local",
                        host = "localhost",
                        port = 6379,
                        database = 0,
                    )
                )
            )
        )
        val useCase = SaveNamespaceAnalysisSettingsUseCase(
            profileStore = store,
            connectionConfig = RedisConnectionConfig(
                name = "local",
                host = "localhost",
                port = 6379,
                database = 0,
            ),
        )

        useCase.save(
            NamespaceAnalysisSettings(
                delimiters = listOf(":", "|"),
                namespaceDepth = 2,
                expectedNamespaces = listOf("user", "session"),
                allowedKeyPatterns = listOf("bull:*"),
                ignoredKeyPatterns = listOf("tmp:*"),
                allowUppercaseInKeys = true,
            )
        )

        val profile = store.load().findProfile("local") ?: error("profile not found")

        assertEquals(listOf(":", "|"), profile.namespaceAnalysisSettings.normalizedDelimiters)
        assertEquals(2, profile.namespaceAnalysisSettings.normalizedNamespaceDepth)
        assertEquals(listOf("user", "session"), profile.namespaceAnalysisSettings.normalizedExpectedNamespaces)
        assertEquals(listOf("bull:*"), profile.namespaceAnalysisSettings.normalizedAllowedKeyPatterns)
        assertEquals(listOf("tmp:*"), profile.namespaceAnalysisSettings.normalizedIgnoredKeyPatterns)
        assertEquals(true, profile.namespaceAnalysisSettings.allowUppercaseInKeys)
    }

    @Test
    fun `save is unavailable for sessions without a persisted matching profile`() {
        val tempDir = createTempDirectory("dioptra-namespace-settings-save-blocked-test")
        val store = HoconConnectionProfileStore(
            configPath = tempDir.resolve("config.conf"),
        )
        val useCase = SaveNamespaceAnalysisSettingsUseCase(
            profileStore = store,
            connectionConfig = RedisConnectionConfig(
                name = "cli-url",
                host = "localhost",
                port = 6379,
                database = 0,
            ),
        )

        assertFalse(useCase.canPersist())
        assertTrue(useCase.unavailableReason().contains("Persistent save unavailable"))
        assertFailsWith<IllegalStateException> {
            useCase.save(NamespaceAnalysisSettings(expectedNamespaces = listOf("user")))
        }
    }
}