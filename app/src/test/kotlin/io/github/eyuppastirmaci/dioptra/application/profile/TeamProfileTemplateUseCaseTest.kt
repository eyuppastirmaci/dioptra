package io.github.eyuppastirmaci.dioptra.application.profile

import io.github.eyuppastirmaci.dioptra.config.DioptraConfig
import io.github.eyuppastirmaci.dioptra.config.HoconConnectionProfileStore
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionProfile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TeamProfileTemplateUseCaseTest {

    @Test
    fun `export active template writes shareable profile without password`() {
        val tempDir = createTempDirectory("dioptra-team-template-export-test")
        val useCase = TeamProfileTemplateUseCase(
            activeConnectionConfig = RedisConnectionConfig(
                name = "Team Cache",
                host = "redis.internal",
                port = 6380,
                database = 2,
                username = "app",
                password = "secret",
                tls = true,
            ),
            profileStore = HoconConnectionProfileStore(configPath = tempDir.resolve("config.conf")),
            templateDirectory = tempDir.resolve("templates"),
            templateImportPath = tempDir.resolve("profile-template.conf"),
        )

        val result = useCase.exportActiveTemplate()

        assertTrue(result.path.exists())
        val exported = HoconConnectionProfileStore(configPath = result.path).load()
        val profile = exported.findProfile("Team Cache")
        assertNotNull(profile)
        assertEquals("redis.internal", profile.host)
        assertEquals("app", profile.username)
        assertEquals(true, profile.tls)
        assertEquals(true, profile.requiresPassword)
    }

    @Test
    fun `import template merges profiles by replacing duplicate names`() {
        val tempDir = createTempDirectory("dioptra-team-template-import-test")
        val profileStore = HoconConnectionProfileStore(configPath = tempDir.resolve("config.conf"))
        val importPath = tempDir.resolve("profile-template.conf")
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
                defaultProfile = "shared",
                profiles = listOf(
                    profile(name = "shared", host = "new.example.com"),
                    profile(name = "team-cache", host = "redis.internal"),
                ),
            )
        )
        val useCase = TeamProfileTemplateUseCase(
            activeConnectionConfig = RedisConnectionConfig(name = "active"),
            profileStore = profileStore,
            templateDirectory = tempDir.resolve("templates"),
            templateImportPath = importPath,
        )

        val result = useCase.importTemplate()

        assertEquals(1, result.addedCount)
        assertEquals(1, result.replacedCount)
        assertEquals(3, result.totalProfiles)
        val loaded = profileStore.load()
        assertEquals("local", loaded.defaultProfile)
        assertEquals("new.example.com", loaded.findProfile("shared")?.host)
        assertEquals("redis.internal", loaded.findProfile("team-cache")?.host)
    }

    private fun profile(
        name: String,
        host: String,
    ): RedisConnectionProfile {
        return RedisConnectionProfile(
            name = name,
            host = host,
        )
    }
}
