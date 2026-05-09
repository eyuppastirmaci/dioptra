package io.github.eyuppastirmaci.dioptra.application.profile

import io.github.eyuppastirmaci.dioptra.config.DioptraConfig
import io.github.eyuppastirmaci.dioptra.config.HoconConnectionProfileStore
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionProfile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileImportExportUseCaseTest {

    @Test
    fun `export writes all profiles to export directory`() {
        val tempDir = createTempDirectory("dioptra-profile-export-test")
        val profileStore = HoconConnectionProfileStore(
            configPath = tempDir.resolve("config.conf"),
        )
        profileStore.save(
            DioptraConfig(
                defaultProfile = "local",
                profiles = listOf(profile("local")),
            )
        )
        val useCase = ProfileImportExportUseCase(
            profileStore = profileStore,
            exportDirectory = tempDir.resolve("exports"),
            importPath = tempDir.resolve("profile-import.conf"),
        )

        val result = useCase.exportProfiles()

        assertEquals(1, result.profileCount)
        assertTrue(result.path.exists())
        val exported = HoconConnectionProfileStore(configPath = result.path).load()
        assertEquals("local", exported.defaultProfile)
        assertNotNull(exported.findProfile("local"))
    }

    @Test
    fun `import merges profiles by replacing duplicate names`() {
        val tempDir = createTempDirectory("dioptra-profile-import-test")
        val profileStore = HoconConnectionProfileStore(
            configPath = tempDir.resolve("config.conf"),
        )
        val importPath = tempDir.resolve("profile-import.conf")
        profileStore.save(
            DioptraConfig(
                defaultProfile = "local",
                profiles = listOf(
                    profile(name = "local", host = "localhost"),
                    profile(name = "shared", host = "old.example.com"),
                ),
            )
        )
        HoconConnectionProfileStore(configPath = importPath).save(
            DioptraConfig(
                defaultProfile = "remote",
                profiles = listOf(
                    profile(name = "remote", host = "redis.example.com"),
                    profile(name = "shared", host = "new.example.com"),
                ),
            )
        )
        val useCase = ProfileImportExportUseCase(
            profileStore = profileStore,
            exportDirectory = tempDir.resolve("exports"),
            importPath = importPath,
        )

        val result = useCase.importProfiles()

        assertEquals(1, result.addedCount)
        assertEquals(1, result.replacedCount)
        assertEquals(3, result.totalProfiles)
        assertEquals("remote", result.defaultProfile)
        val loaded = profileStore.load()
        assertEquals("redis.example.com", loaded.findProfile("remote")?.host)
        assertEquals("new.example.com", loaded.findProfile("shared")?.host)
        assertEquals("localhost", loaded.findProfile("local")?.host)
    }

    private fun profile(
        name: String,
        host: String = "localhost",
    ): RedisConnectionProfile {
        return RedisConnectionProfile(
            name = name,
            host = host,
        )
    }
}
