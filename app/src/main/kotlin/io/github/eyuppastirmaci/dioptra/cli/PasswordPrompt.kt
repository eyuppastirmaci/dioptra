package io.github.eyuppastirmaci.dioptra.cli

object PasswordPrompt {

    fun readPassword(prompt: String = "Redis password: "): String? {
        val console = System.console() ?: return null
        val password = console.readPassword(prompt) ?: return null

        return password.concatToString()
    }
}
