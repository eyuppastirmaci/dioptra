package io.github.eyuppastirmaci.dioptra.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class HoconLastUsedConnectionStore(
    private val metadataPath: Path = defaultMetadataPath(),
) {

    fun load(): LastUsedConnectionMetadata? {
        if (!metadataPath.exists()) {
            return null
        }

        val config = ConfigFactory.parseFile(metadataPath.toFile()).resolve()

        return LastUsedConnectionMetadata(
            profileName = config.optionalString("profileName"),
            host = config.optionalString("host") ?: "localhost",
            port = config.optionalInt("port") ?: 6379,
            database = config.optionalInt("database") ?: 0,
            username = config.optionalString("username"),
            tls = config.optionalBoolean("tls") ?: false,
            lastConnectedAt = config.optionalString("lastConnectedAt")?.let(Instant::parse) ?: Instant.EPOCH,
        )
    }

    fun save(metadata: LastUsedConnectionMetadata) {
        metadataPath.parent?.createDirectories()
        metadataPath.writeText(
            text = render(metadata),
            charset = StandardCharsets.UTF_8,
        )
    }

    private fun render(metadata: LastUsedConnectionMetadata): String {
        return buildString {
            metadata.profileName?.let {
                append("profileName = ")
                appendQuoted(it)
                appendLine()
            }
            append("host = ")
            appendQuoted(metadata.host)
            appendLine()
            appendLine("port = ${metadata.port}")
            appendLine("database = ${metadata.database}")
            metadata.username?.let {
                append("username = ")
                appendQuoted(it)
                appendLine()
            }
            appendLine("tls = ${metadata.tls}")
            append("lastConnectedAt = ")
            appendQuoted(metadata.lastConnectedAt.toString())
            appendLine()
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

    private fun Config.optionalString(path: String): String? {
        return if (hasPath(path)) getString(path) else null
    }

    private fun Config.optionalInt(path: String): Int? {
        return if (hasPath(path)) getInt(path) else null
    }

    private fun Config.optionalBoolean(path: String): Boolean? {
        return if (hasPath(path)) getBoolean(path) else null
    }

    companion object {
        fun defaultMetadataPath(): Path {
            return Path.of(System.getProperty("user.home"), ".dioptra", "last-used.conf")
        }
    }
}
