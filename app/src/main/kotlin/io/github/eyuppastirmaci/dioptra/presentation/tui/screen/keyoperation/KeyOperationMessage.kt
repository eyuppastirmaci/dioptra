package io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keyoperation

sealed interface KeyOperationMessage {
    val text: String

    data class Info(
        override val text: String,
    ) : KeyOperationMessage

    data class Success(
        override val text: String,
    ) : KeyOperationMessage

    data class Failure(
        override val text: String,
    ) : KeyOperationMessage
}

data class KeyOperationToast(
    val message: KeyOperationMessage,
    val details: List<String> = emptyList(),
    val expiresAtMillis: Long? = null,
) {

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return expiresAtMillis != null && nowMillis >= expiresAtMillis
    }

    companion object {
        fun persistent(
            message: KeyOperationMessage,
            vararg details: String,
        ): KeyOperationToast {
            return KeyOperationToast(
                message = message,
                details = details.filter { it.isNotBlank() },
            )
        }

        fun transient(
            message: KeyOperationMessage,
            vararg details: String,
            durationMillis: Long = DEFAULT_DURATION_MILLIS,
        ): KeyOperationToast {
            return KeyOperationToast(
                message = message,
                details = details.filter { it.isNotBlank() },
                expiresAtMillis = System.currentTimeMillis() + durationMillis,
            )
        }

        private const val DEFAULT_DURATION_MILLIS = 2_500L
    }
}
