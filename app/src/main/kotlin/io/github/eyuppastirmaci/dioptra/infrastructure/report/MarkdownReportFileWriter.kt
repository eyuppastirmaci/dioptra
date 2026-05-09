package io.github.eyuppastirmaci.dioptra.infrastructure.report

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class MarkdownReportFileWriter(
    private val reportsDirectory: Path = defaultReportsDirectory(),
) {

    fun write(
        markdown: String,
        generatedAt: Instant,
        profileName: String,
    ): Path {
        reportsDirectory.createDirectories()
        val filename = "dioptra-${sanitizeFilename(profileName)}-${FILENAME_TIMESTAMP.format(generatedAt)}.md"
        val path = reportsDirectory.resolve(filename)
        path.writeText(
            text = markdown,
            charset = StandardCharsets.UTF_8,
        )
        return path
    }

    private fun sanitizeFilename(value: String): String {
        return value
            .lowercase()
            .map { character ->
                when {
                    character.isLetterOrDigit() -> character
                    character == '-' || character == '_' -> character
                    else -> '-'
                }
            }
            .joinToString(separator = "")
            .trim('-')
            .ifBlank { "redis" }
    }

    companion object {
        fun defaultReportsDirectory(): Path {
            return Path.of(System.getProperty("user.home"), ".dioptra", "reports")
        }

        private val FILENAME_TIMESTAMP: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
    }
}
