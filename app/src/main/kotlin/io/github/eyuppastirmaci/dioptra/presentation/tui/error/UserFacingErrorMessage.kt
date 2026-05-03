package io.github.eyuppastirmaci.dioptra.presentation.tui.error

import io.github.eyuppastirmaci.dioptra.config.CredentialMasker

object UserFacingErrorMessage {

    fun from(throwable: Throwable): String {
        val message = throwable.message
            ?.takeIf { it.isNotBlank() }
            ?: throwable::class.simpleName
            ?: "unknown error"

        return CredentialMasker.maskSensitiveValues(message)
    }
}
