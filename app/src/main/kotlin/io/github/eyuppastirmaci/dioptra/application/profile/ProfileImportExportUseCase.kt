package io.github.eyuppastirmaci.dioptra.application.profile

import io.github.eyuppastirmaci.dioptra.config.DioptraConfig
import io.github.eyuppastirmaci.dioptra.config.HoconConnectionProfileStore
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class ProfileImportExportUseCase(
    private val profileStore: HoconConnectionProfileStore = HoconConnectionProfileStore(),
    private val exportDirectory: Path = defaultExportDirectory(),
    private val importPath: Path = defaultImportPath(),
) {

    fun summary(): ProfileImportExportSummary {
        val config = profileStore.load()
        return ProfileImportExportSummary(
            profileCount = config.profiles.size,
            defaultProfile = config.defaultProfile,
            exportDirectory = exportDirectory,
            importPath = importPath,
        )
    }

    fun exportProfiles(): ProfileExportResult {
        val config = profileStore.load()
        require(config.profiles.isNotEmpty()) {
            "There are no saved profiles to export."
        }

        exportDirectory.createDirectories()
        val path = exportDirectory.resolve("dioptra-profiles-${timestamp()}.conf")
        HoconConnectionProfileStore(configPath = path).save(config)

        return ProfileExportResult(
            path = path,
            profileCount = config.profiles.size,
        )
    }

    fun importProfiles(): ProfileImportResult {
        require(importPath.exists()) {
            "Import file not found: $importPath"
        }

        val importedConfig = HoconConnectionProfileStore(configPath = importPath).load()
        require(importedConfig.profiles.isNotEmpty()) {
            "Import file does not contain any profiles."
        }

        val currentConfig = profileStore.load()
        val currentNames = currentConfig.profiles.map { it.name }.toSet()
        val importedByName = importedConfig.profiles.associateBy { it.name }
        val mergedProfiles = currentConfig.profiles
            .filterNot { it.name in importedByName }
            .plus(importedConfig.profiles)
            .sortedBy { it.name }

        val defaultProfile = importedConfig.defaultProfile
            ?.takeIf { defaultName -> mergedProfiles.any { it.name == defaultName } }
            ?: currentConfig.defaultProfile?.takeIf { defaultName -> mergedProfiles.any { it.name == defaultName } }

        profileStore.save(
            DioptraConfig(
                defaultProfile = defaultProfile,
                profiles = mergedProfiles,
            )
        )

        return ProfileImportResult(
            addedCount = importedConfig.profiles.count { it.name !in currentNames },
            replacedCount = importedConfig.profiles.count { it.name in currentNames },
            totalProfiles = mergedProfiles.size,
            defaultProfile = defaultProfile,
        )
    }

    private fun timestamp(): String {
        return LocalDateTime.now().format(EXPORT_TIMESTAMP_FORMAT)
    }

    companion object {
        private val EXPORT_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        fun defaultExportDirectory(): Path {
            return Path.of(System.getProperty("user.home"), ".dioptra", "profile-exports")
        }

        fun defaultImportPath(): Path {
            return Path.of(System.getProperty("user.home"), ".dioptra", "profile-import.conf")
        }
    }
}

data class ProfileImportExportSummary(
    val profileCount: Int,
    val defaultProfile: String?,
    val exportDirectory: Path,
    val importPath: Path,
)

data class ProfileExportResult(
    val path: Path,
    val profileCount: Int,
)

data class ProfileImportResult(
    val addedCount: Int,
    val replacedCount: Int,
    val totalProfiles: Int,
    val defaultProfile: String?,
)
