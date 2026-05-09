package io.github.eyuppastirmaci.dioptra.application.profile

import io.github.eyuppastirmaci.dioptra.config.DioptraConfig
import io.github.eyuppastirmaci.dioptra.config.HoconConnectionProfileStore
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionProfile
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class TeamProfileTemplateUseCase(
    private val activeConnectionConfig: RedisConnectionConfig,
    private val profileStore: HoconConnectionProfileStore = HoconConnectionProfileStore(),
    private val templateDirectory: Path = defaultTemplateDirectory(),
    private val templateImportPath: Path = defaultTemplateImportPath(),
) {

    fun summary(): TeamProfileTemplateSummary {
        return TeamProfileTemplateSummary(
            activeProfileName = activeConnectionConfig.name,
            savedProfileCount = profileStore.load().profiles.size,
            templateDirectory = templateDirectory,
            templateImportPath = templateImportPath,
        )
    }

    fun exportActiveTemplate(): TeamProfileTemplateExportResult {
        templateDirectory.createDirectories()
        val profile = activeConnectionConfig.toShareableProfile()
        val path = templateDirectory.resolve("dioptra-template-${profile.name.sanitized()}-${timestamp()}.conf")
        HoconConnectionProfileStore(configPath = path).save(
            DioptraConfig(
                defaultProfile = profile.name,
                profiles = listOf(profile),
            )
        )

        return TeamProfileTemplateExportResult(
            path = path,
            profileName = profile.name,
        )
    }

    fun importTemplate(): TeamProfileTemplateImportResult {
        require(templateImportPath.exists()) {
            "Template import file not found: $templateImportPath"
        }

        val templateConfig = HoconConnectionProfileStore(configPath = templateImportPath).load()
        require(templateConfig.profiles.isNotEmpty()) {
            "Template import file does not contain any profiles."
        }

        val currentConfig = profileStore.load()
        val currentNames = currentConfig.profiles.map { it.name }.toSet()
        val templateProfilesByName = templateConfig.profiles.associateBy { it.name }
        val mergedProfiles = currentConfig.profiles
            .filterNot { it.name in templateProfilesByName }
            .plus(templateConfig.profiles)
            .sortedBy { it.name }

        val defaultProfile = currentConfig.defaultProfile
            ?.takeIf { defaultName -> mergedProfiles.any { it.name == defaultName } }
            ?: templateConfig.defaultProfile?.takeIf { defaultName -> mergedProfiles.any { it.name == defaultName } }

        profileStore.save(
            DioptraConfig(
                defaultProfile = defaultProfile,
                profiles = mergedProfiles,
            )
        )

        return TeamProfileTemplateImportResult(
            addedCount = templateConfig.profiles.count { it.name !in currentNames },
            replacedCount = templateConfig.profiles.count { it.name in currentNames },
            totalProfiles = mergedProfiles.size,
        )
    }

    private fun RedisConnectionConfig.toShareableProfile(): RedisConnectionProfile {
        return RedisConnectionProfile(
            name = name,
            host = host,
            port = port,
            database = database,
            username = username,
            tls = tls,
            timeoutMillis = timeoutMillis,
            requiresPassword = !password.isNullOrBlank(),
            namespaceAnalysisSettings = namespaceAnalysisSettings,
            riskAnalysisSettings = riskAnalysisSettings,
        )
    }

    private fun String.sanitized(): String {
        return lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { "profile" }
    }

    private fun timestamp(): String {
        return LocalDateTime.now().format(TEMPLATE_TIMESTAMP_FORMAT)
    }

    companion object {
        private val TEMPLATE_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        fun defaultTemplateDirectory(): Path {
            return Path.of(System.getProperty("user.home"), ".dioptra", "profile-templates")
        }

        fun defaultTemplateImportPath(): Path {
            return Path.of(System.getProperty("user.home"), ".dioptra", "profile-template.conf")
        }
    }
}

data class TeamProfileTemplateSummary(
    val activeProfileName: String,
    val savedProfileCount: Int,
    val templateDirectory: Path,
    val templateImportPath: Path,
)

data class TeamProfileTemplateExportResult(
    val path: Path,
    val profileName: String,
)

data class TeamProfileTemplateImportResult(
    val addedCount: Int,
    val replacedCount: Int,
    val totalProfiles: Int,
)
