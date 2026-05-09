package io.github.eyuppastirmaci.dioptra.cli

data class CliConnectionOptions(
    val url: String? = null,
    val profile: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val database: Int? = null,
    val username: String? = null,
    val password: String? = null,
    val passwordRequested: Boolean = false,
    val tls: Boolean = false,
)

data class CliOptions(
    val connection: CliConnectionOptions = CliConnectionOptions(),
    val debug: Boolean = false,
    val readOnly: Boolean = false,
    val productionSafety: Boolean = false,
    val protectedNamespaces: List<String> = emptyList(),
)
