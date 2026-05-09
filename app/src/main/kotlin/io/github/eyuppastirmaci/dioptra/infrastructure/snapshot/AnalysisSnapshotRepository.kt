package io.github.eyuppastirmaci.dioptra.infrastructure.snapshot

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.github.eyuppastirmaci.dioptra.domain.snapshot.AnalysisSnapshot
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

data class AnalysisSnapshotFile(
    val path: Path,
    val fileName: String,
    val generatedAt: String,
    val profileName: String,
    val selectedDatabase: Int,
    val schemaVersion: Int,
)

class AnalysisSnapshotRepository(
    private val snapshotsDirectory: Path = AnalysisSnapshotFileWriter.defaultSnapshotsDirectory(),
    private val gson: Gson = GsonBuilder().create(),
) {

    fun list(): List<AnalysisSnapshotFile> {
        if (!snapshotsDirectory.exists()) {
            return emptyList()
        }

        val paths = Files.list(snapshotsDirectory).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.extension.equals("json", ignoreCase = true) }
                .toList()
        }

        return paths
            .mapNotNull { path -> path.toSnapshotFileOrNull() }
            .sortedByDescending { it.generatedAt }
    }

    fun load(path: Path): AnalysisSnapshot {
        return gson.fromJson(path.readText(StandardCharsets.UTF_8), AnalysisSnapshot::class.java)
    }

    private fun Path.toSnapshotFileOrNull(): AnalysisSnapshotFile? {
        return runCatching {
            val snapshot = load(this)
            AnalysisSnapshotFile(
                path = this,
                fileName = name,
                generatedAt = snapshot.generatedAt,
                profileName = snapshot.profileName,
                selectedDatabase = snapshot.selectedDatabase,
                schemaVersion = snapshot.schemaVersion,
            )
        }.getOrNull()
    }
}
