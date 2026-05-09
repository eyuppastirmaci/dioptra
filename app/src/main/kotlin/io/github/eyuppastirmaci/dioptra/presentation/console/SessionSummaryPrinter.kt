package io.github.eyuppastirmaci.dioptra.presentation.console

import io.github.eyuppastirmaci.dioptra.application.safety.OperationAuditResult
import io.github.eyuppastirmaci.dioptra.application.session.SessionSummary
import java.time.Duration

class SessionSummaryPrinter {

    fun print(summary: SessionSummary) {
        println()
        println(DIVIDER)
        println("  Dioptra Session Summary")
        println(DIVIDER)

        if (!summary.hasConnection) {
            println("  No connection established.")
            println(DIVIDER)
            println()
            return
        }

        println("  Profile      : ${summary.profileName.ifBlank { "(none)" }}")
        println("  Connection   : ${summary.maskedUri}")
        println("  Database     : ${summary.database}")
        println("  Mode         : ${formatMode(summary)}")
        println("  Duration     : ${formatDuration(summary.duration)}")

        if (summary.screenVisits.isNotEmpty()) {
            println()
            println("  Screens visited  : ${summary.screenVisits.joinToString { it.label }}")
        }

        if (summary.keysBrowsedPageCount > 0 || summary.keysInspectedCount > 0) {
            println()
            if (summary.keysBrowsedPageCount > 0) {
                val pageWord = if (summary.keysBrowsedPageCount == 1) "page" else "pages"
                println("  Keys scanned     : ${formatNumber(summary.keysBrowsedCount)} (${summary.keysBrowsedPageCount} $pageWord)")
            }
            if (summary.keysInspectedCount > 0) {
                println("  Keys inspected   : ${summary.keysInspectedCount}")
            }
        }

        val significantOps = summary.operationResults.filterKeys { action ->
            summary.operationResults[action]?.keys?.any { it != OperationAuditResult.Started } == true
        }
        if (significantOps.isNotEmpty()) {
            println()
            println("  Operations")
            significantOps.forEach { (action, results) ->
                val parts = results
                    .filterKeys { it != OperationAuditResult.Started }
                    .entries
                    .sortedBy { it.key.ordinal }
                    .map { (result, count) -> "$count ${result.value}" }
                println("    ${formatAction(action)} : ${parts.joinToString(" / ")}")
            }
        }

        println(DIVIDER)
        println()
    }

    private fun formatMode(summary: SessionSummary): String {
        return buildString {
            append(if (summary.readOnly) "Read-only" else "Read-write")
            if (summary.productionSafety) {
                append(" / Production Safety ON")
            }
        }
    }

    private fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.seconds
        return when {
            totalSeconds < 60 -> "${totalSeconds}s"
            totalSeconds < 3600 -> {
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
            }
            else -> {
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
            }
        }
    }

    private fun formatNumber(value: Int): String {
        return "%,d".format(value)
    }

    private fun formatAction(action: String): String {
        return action
            .split("-")
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
    }

    private companion object {
        const val DIVIDER = "══════════════════════════════════════"
    }
}
