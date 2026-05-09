package io.github.eyuppastirmaci.dioptra.config

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class HoconConnectionProfileStoreTest {

    @Test
    fun `save and load preserves namespace analysis settings`() {
        val tempDir = createTempDirectory("dioptra-profile-store-test")
        val store = HoconConnectionProfileStore(
            configPath = tempDir.resolve("config.conf"),
        )
        val profile = RedisConnectionProfile(
            name = "local",
            host = "localhost",
            port = 6379,
            database = 1,
            namespaceAnalysisSettings = NamespaceAnalysisSettings(
                delimiters = listOf(":", "."),
                namespaceDepth = 2,
                expectedNamespaces = listOf("user", "cache"),
                allowedKeyPatterns = listOf("bull:*", "tenant:*:lock:*"),
                ignoredKeyPatterns = listOf("__redis__:*", "tmp:*") ,
                allowWhitespaceInKeys = true,
                allowUppercaseInKeys = true,
                allowRepeatedDelimiters = true,
            ),
            riskAnalysisSettings = RiskAnalysisSettings(
                bigKeyThresholdBytes = 2_048,
                largeHashFieldThreshold = 101,
                largeListLengthThreshold = 102,
                largeSetMemberThreshold = 103,
                largeZsetMemberThreshold = 104,
                largeStreamEntryThreshold = 105,
                topKeyCount = 7,
                scanCount = 250,
            ),
        )

        store.save(
            DioptraConfig(
                defaultProfile = "local",
                profiles = listOf(profile),
            )
        )

        val loaded = store.load()
        val loadedProfile = loaded.findProfile("local") ?: error("profile not found")

        assertEquals(listOf(":", "."), loadedProfile.namespaceAnalysisSettings.normalizedDelimiters)
        assertEquals(2, loadedProfile.namespaceAnalysisSettings.normalizedNamespaceDepth)
        assertEquals(listOf("user", "cache"), loadedProfile.namespaceAnalysisSettings.normalizedExpectedNamespaces)
        assertEquals(listOf("bull:*", "tenant:*:lock:*"), loadedProfile.namespaceAnalysisSettings.normalizedAllowedKeyPatterns)
        assertEquals(listOf("__redis__:*", "tmp:*"), loadedProfile.namespaceAnalysisSettings.normalizedIgnoredKeyPatterns)
        assertEquals(true, loadedProfile.namespaceAnalysisSettings.allowWhitespaceInKeys)
        assertEquals(true, loadedProfile.namespaceAnalysisSettings.allowUppercaseInKeys)
        assertEquals(true, loadedProfile.namespaceAnalysisSettings.allowRepeatedDelimiters)
        assertEquals(2_048L, loadedProfile.riskAnalysisSettings.normalizedBigKeyThresholdBytes)
        assertEquals(101L, loadedProfile.riskAnalysisSettings.normalizedLargeHashFieldThreshold)
        assertEquals(102L, loadedProfile.riskAnalysisSettings.normalizedLargeListLengthThreshold)
        assertEquals(103L, loadedProfile.riskAnalysisSettings.normalizedLargeSetMemberThreshold)
        assertEquals(104L, loadedProfile.riskAnalysisSettings.normalizedLargeZsetMemberThreshold)
        assertEquals(105L, loadedProfile.riskAnalysisSettings.normalizedLargeStreamEntryThreshold)
        assertEquals(7, loadedProfile.riskAnalysisSettings.normalizedTopKeyCount)
        assertEquals(250L, loadedProfile.riskAnalysisSettings.normalizedScanCount)
    }
}
