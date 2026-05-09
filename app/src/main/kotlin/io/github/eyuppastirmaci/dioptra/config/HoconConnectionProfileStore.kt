package io.github.eyuppastirmaci.dioptra.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class HoconConnectionProfileStore(
    private val configPath: Path = defaultConfigPath(),
) {

    fun load(): DioptraConfig {
        if (!configPath.exists()) {
            return DioptraConfig()
        }

        val config = ConfigFactory.parseFile(configPath.toFile()).resolve()
        val defaultProfile = config.optionalString("defaultProfile")
        val profiles = if (config.hasPath("profiles")) {
            config.getConfigList("profiles").map(::readProfile)
        } else {
            emptyList()
        }

        return DioptraConfig(
            defaultProfile = defaultProfile,
            profiles = profiles,
        )
    }

    fun save(config: DioptraConfig) {
        configPath.parent?.createDirectories()
        configPath.writeText(
            text = render(config),
            charset = StandardCharsets.UTF_8,
        )
    }

    fun saveProfile(
        profile: RedisConnectionProfile,
        setAsDefault: Boolean = false,
    ) {
        val current = load()
        val profiles = current.profiles
            .filterNot { it.name == profile.name }
            .plus(profile)
            .sortedBy { it.name }

        save(
            current.copy(
                defaultProfile = if (setAsDefault) profile.name else current.defaultProfile,
                profiles = profiles,
            ),
        )
    }

    fun deleteProfile(name: String) {
        val current = load()
        val profiles = current.profiles.filterNot { it.name == name }
        val defaultProfile = current.defaultProfile.takeUnless { it == name }

        save(
            current.copy(
                defaultProfile = defaultProfile,
                profiles = profiles,
            ),
        )
    }

    private fun readProfile(config: Config): RedisConnectionProfile {
        return RedisConnectionProfile(
            name = config.requiredString("name"),
            host = config.optionalString("host") ?: "localhost",
            port = config.optionalInt("port") ?: 6379,
            database = config.optionalInt("database") ?: 0,
            username = config.optionalString("username"),
            tls = config.optionalBoolean("tls") ?: false,
            timeoutMillis = config.optionalLong("timeoutMillis") ?: 5_000,
            requiresPassword = config.optionalBoolean("requiresPassword") ?: false,
            namespaceAnalysisSettings = readNamespaceAnalysisSettings(config),
        )
    }

    private fun readNamespaceAnalysisSettings(config: Config): NamespaceAnalysisSettings {
        if (!config.hasPath("analysis")) {
            return NamespaceAnalysisSettings()
        }

        val analysisConfig = config.getConfig("analysis")
        return NamespaceAnalysisSettings(
            delimiters = analysisConfig.optionalStringList("delimiters") ?: listOf(":"),
            namespaceDepth = analysisConfig.optionalInt("namespaceDepth") ?: 1,
            expectedNamespaces = analysisConfig.optionalStringList("expectedNamespaces") ?: emptyList(),
            allowedKeyPatterns = analysisConfig.optionalStringList("allowedKeyPatterns") ?: emptyList(),
            ignoredKeyPatterns = analysisConfig.optionalStringList("ignoredKeyPatterns") ?: emptyList(),
            allowWhitespaceInKeys = analysisConfig.optionalBoolean("allowWhitespaceInKeys") ?: false,
            allowUppercaseInKeys = analysisConfig.optionalBoolean("allowUppercaseInKeys") ?: false,
            allowRepeatedDelimiters = analysisConfig.optionalBoolean("allowRepeatedDelimiters") ?: false,
        )
    }

    private fun render(config: DioptraConfig): String {
        return buildString {
            config.defaultProfile?.let {
                append("defaultProfile = ")
                appendQuoted(it)
                appendLine()
                appendLine()
            }

            appendLine("profiles = [")
            config.profiles.forEachIndexed { index, profile ->
                appendLine("  {")
                append("    name = ")
                appendQuoted(profile.name)
                appendLine()
                append("    host = ")
                appendQuoted(profile.host)
                appendLine()
                appendLine("    port = ${profile.port}")
                appendLine("    database = ${profile.database}")
                profile.username?.let {
                    append("    username = ")
                    appendQuoted(it)
                    appendLine()
                }
                appendLine("    tls = ${profile.tls}")
                appendLine("    timeoutMillis = ${profile.timeoutMillis}")
                appendLine("    requiresPassword = ${profile.requiresPassword}")
                appendLine("    analysis {")
                append("      delimiters = [")
                profile.namespaceAnalysisSettings.normalizedDelimiters.forEachIndexed { delimiterIndex, delimiter ->
                    if (delimiterIndex > 0) {
                        append(", ")
                    }
                    appendQuoted(delimiter)
                }
                appendLine("]")
                appendLine("      namespaceDepth = ${profile.namespaceAnalysisSettings.normalizedNamespaceDepth}")
                append("      expectedNamespaces = [")
                profile.namespaceAnalysisSettings.normalizedExpectedNamespaces.forEachIndexed { namespaceIndex, namespace ->
                    if (namespaceIndex > 0) {
                        append(", ")
                    }
                    appendQuoted(namespace)
                }
                appendLine("]")
                append("      allowedKeyPatterns = [")
                profile.namespaceAnalysisSettings.normalizedAllowedKeyPatterns.forEachIndexed { patternIndex, pattern ->
                    if (patternIndex > 0) {
                        append(", ")
                    }
                    appendQuoted(pattern)
                }
                appendLine("]")
                append("      ignoredKeyPatterns = [")
                profile.namespaceAnalysisSettings.normalizedIgnoredKeyPatterns.forEachIndexed { patternIndex, pattern ->
                    if (patternIndex > 0) {
                        append(", ")
                    }
                    appendQuoted(pattern)
                }
                appendLine("]")
                appendLine("      allowWhitespaceInKeys = ${profile.namespaceAnalysisSettings.allowWhitespaceInKeys}")
                appendLine("      allowUppercaseInKeys = ${profile.namespaceAnalysisSettings.allowUppercaseInKeys}")
                appendLine("      allowRepeatedDelimiters = ${profile.namespaceAnalysisSettings.allowRepeatedDelimiters}")
                appendLine("    }")
                append("  }")
                if (index < config.profiles.lastIndex) {
                    append(',')
                }
                appendLine()
            }
            appendLine("]")
        }
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    private fun Config.requiredString(path: String): String {
        return getString(path)
    }

    private fun Config.optionalString(path: String): String? {
        return if (hasPath(path)) getString(path) else null
    }

    private fun Config.optionalInt(path: String): Int? {
        return if (hasPath(path)) getInt(path) else null
    }

    private fun Config.optionalLong(path: String): Long? {
        return if (hasPath(path)) getLong(path) else null
    }

    private fun Config.optionalBoolean(path: String): Boolean? {
        return if (hasPath(path)) getBoolean(path) else null
    }

    private fun Config.optionalStringList(path: String): List<String>? {
        return if (hasPath(path)) getStringList(path) else null
    }

    companion object {
        fun defaultConfigPath(): Path {
            return Path.of(System.getProperty("user.home"), ".dioptra", "config.conf")
        }
    }
}
