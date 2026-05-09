package io.github.eyuppastirmaci.dioptra.infrastructure.snapshot

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.eyuppastirmaci.dioptra.domain.snapshot.AnalysisSnapshot
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class AnalysisSnapshotFileWriter(
    private val snapshotsDirectory: Path = defaultSnapshotsDirectory(),
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
) {

    fun write(
        snapshot: AnalysisSnapshot,
        generatedAt: Instant,
        profileName: String,
    ): Path {
        snapshotsDirectory.createDirectories()
        val filename = "dioptra-${sanitizeFilename(profileName)}-${FILENAME_TIMESTAMP.format(generatedAt)}.json"
        val path = snapshotsDirectory.resolve(filename)
        path.writeText(
            text = gson.toJson(snapshot),
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
        fun defaultSnapshotsDirectory(): Path {
            return Path.of(System.getProperty("user.home"), ".dioptra", "snapshots")
        }

        private val FILENAME_TIMESTAMP: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
    }
}
