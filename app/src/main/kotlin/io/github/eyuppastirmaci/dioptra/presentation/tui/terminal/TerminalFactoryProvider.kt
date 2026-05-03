package io.github.eyuppastirmaci.dioptra.presentation.tui.terminal

object TerminalFactoryProvider {

    fun create(): TerminalFactory {
        return when (resolveTerminalMode()) {
            TerminalMode.SWING -> SwingTerminalFactory()
            TerminalMode.NATIVE -> NativeTerminalFactory()
            TerminalMode.AUTO -> createAutoDetectedFactory()
        }
    }

    private fun createAutoDetectedFactory(): TerminalFactory {
        return if (isWindows()) {
            SwingTerminalFactory()
        } else {
            NativeTerminalFactory()
        }
    }

    private fun resolveTerminalMode(): TerminalMode {
        val rawMode = System
            .getProperty("dioptra.terminal", "auto")
            .trim()
            .lowercase()

        return when (rawMode) {
            "swing" -> TerminalMode.SWING
            "native" -> TerminalMode.NATIVE
            else -> TerminalMode.AUTO
        }
    }

    private fun isWindows(): Boolean {
        return System
            .getProperty("os.name")
            .lowercase()
            .contains("win")
    }

    private enum class TerminalMode {
        AUTO,
        SWING,
        NATIVE,
    }
}