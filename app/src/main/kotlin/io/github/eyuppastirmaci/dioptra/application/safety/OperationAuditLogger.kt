package io.github.eyuppastirmaci.dioptra.application.safety

import io.github.eyuppastirmaci.dioptra.application.session.SessionTracker
import org.slf4j.LoggerFactory

data class OperationAuditContext(
    val profileName: String,
    val database: Int,
    val maskedUri: String,
    val readOnly: Boolean,
    val productionSafety: Boolean,
)

enum class OperationAuditResult(
    val value: String,
) {
    Started("started"),
    Success("success"),
    Missing("missing"),
    Blocked("blocked"),
    Failure("failure"),
}

class OperationAuditLogger(
    private val context: OperationAuditContext,
    private val sessionTracker: SessionTracker? = null,
) {
    private val logger = LoggerFactory.getLogger(AUDIT_LOGGER_NAME)

    fun record(
        action: String,
        keyName: String,
        target: String,
        result: OperationAuditResult,
        details: Map<String, String?> = emptyMap(),
    ) {
        val detailText = details
            .filterValues { !it.isNullOrBlank() }
            .map { (key, value) -> "${key.sanitizeKey()}=${value.orEmpty().quoteValue()}" }
            .joinToString(separator = " ")

        val message = buildString {
            append("event=redis_operation")
            append(" action=${action.quoteValue()}")
            append(" result=${result.value.quoteValue()}")
            append(" profile=${context.profileName.quoteValue()}")
            append(" database=${context.database}")
            append(" connection=${context.maskedUri.quoteValue()}")
            append(" key=${keyName.quoteValue()}")
            append(" target=${target.quoteValue()}")
            append(" readOnly=${context.readOnly}")
            append(" productionSafety=${context.productionSafety}")
            if (detailText.isNotBlank()) {
                append(' ')
                append(detailText)
            }
        }

        logger.info(message)
        sessionTracker?.recordOperation(action, result)
    }

    companion object {
        private const val AUDIT_LOGGER_NAME = "io.github.eyuppastirmaci.dioptra.audit"
    }
}

private fun String.quoteValue(): String {
    return "\"${singleLine().replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

private fun String.singleLine(): String {
    return replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
}

private fun String.sanitizeKey(): String {
    return filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        .ifBlank { "detail" }
}
