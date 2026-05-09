package io.github.eyuppastirmaci.dioptra.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class HoconNamespaceBookmarkStore(
    private val bookmarksPath: Path = defaultBookmarksPath(),
) {

    fun load(profileName: String): Set<String> {
        if (!bookmarksPath.exists()) {
            return emptySet()
        }

        val normalizedProfile = normalizeProfileName(profileName)
        val config = ConfigFactory.parseFile(bookmarksPath.toFile()).resolve()
        if (!config.hasPath("profiles")) {
            return emptySet()
        }

        return config.getConfigList("profiles")
            .firstOrNull { it.optionalString("name") == normalizedProfile }
            ?.optionalStringList("namespaces")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSortedSet()
            ?: emptySet()
    }

    fun save(
        profileName: String,
        namespaces: Set<String>,
    ) {
        val normalizedProfile = normalizeProfileName(profileName)
        val allBookmarks = loadAll()
            .toMutableMap()
            .apply {
                if (namespaces.isEmpty()) {
                    remove(normalizedProfile)
                } else {
                    put(normalizedProfile, namespaces.map { it.trim() }.filter { it.isNotBlank() }.toSortedSet())
                }
            }

        bookmarksPath.parent?.createDirectories()
        bookmarksPath.writeText(
            text = render(allBookmarks),
            charset = StandardCharsets.UTF_8,
        )
    }

    private fun loadAll(): Map<String, Set<String>> {
        if (!bookmarksPath.exists()) {
            return emptyMap()
        }

        val config = ConfigFactory.parseFile(bookmarksPath.toFile()).resolve()
        if (!config.hasPath("profiles")) {
            return emptyMap()
        }

        return config.getConfigList("profiles")
            .mapNotNull { profileConfig ->
                val name = profileConfig.optionalString("name") ?: return@mapNotNull null
                val namespaces = profileConfig.optionalStringList("namespaces")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.toSortedSet()
                    ?: emptySet()
                name to namespaces
            }
            .toMap()
    }

    private fun render(bookmarks: Map<String, Set<String>>): String {
        return buildString {
            appendLine("profiles = [")
            bookmarks.toSortedMap().forEach { (profileName, namespaces) ->
                appendLine("  {")
                append("    name = ")
                appendQuoted(profileName)
                appendLine()
                appendLine("    namespaces = [")
                namespaces.sorted().forEach { namespace ->
                    append("      ")
                    appendQuoted(namespace)
                    appendLine(",")
                }
                appendLine("    ]")
                appendLine("  },")
            }
            appendLine("]")
        }
    }

    private fun normalizeProfileName(profileName: String): String {
        return profileName.trim().ifBlank { DEFAULT_PROFILE_NAME }
    }

    private fun Config.optionalString(path: String): String? {
        return if (hasPath(path)) getString(path) else null
    }

    private fun Config.optionalStringList(path: String): List<String>? {
        return if (hasPath(path)) getStringList(path) else null
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

    companion object {
        private const val DEFAULT_PROFILE_NAME = "default"

        fun defaultBookmarksPath(): Path {
            return Path.of(System.getProperty("user.home"), ".dioptra", "bookmarks.conf")
        }
    }
}
