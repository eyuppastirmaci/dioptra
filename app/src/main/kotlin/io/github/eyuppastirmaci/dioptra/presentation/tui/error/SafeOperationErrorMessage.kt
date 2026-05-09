package io.github.eyuppastirmaci.dioptra.presentation.tui.error

object SafeOperationErrorMessage {

    fun from(throwable: Throwable): String {
        return UserFacingErrorMessage.from(throwable)
            .replace(Regex("\\s+"), " ")
            .take(MAX_LENGTH)
            .ifBlank { "Operation failed." }
    }

    private const val MAX_LENGTH = 96
}
