package io.github.eyuppastirmaci.dioptra.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

class DioptraCommand : CliktCommand(
    name = "dioptra",
) {
    private val url: String? by option(
        "--url",
        help = "Redis connection URL. Credentials are accepted but never logged without masking.",
    )

    private val profile: String? by option(
        "--profile",
        help = "Connection profile name from ~/.dioptra/config.conf.",
    )

    private val host: String? by option(
        "--host",
        help = "Redis host.",
    )

    private val port: Int? by option(
        "--port",
        help = "Redis port.",
    ).int()

    private val database: Int? by option(
        "--database",
        help = "Redis database index.",
    ).int()

    private val username: String? by option(
        "--username",
        help = "Redis ACL username.",
    )

    private val passwordRequested: Boolean by option(
        "--password",
        help = "Prompt for the Redis password without storing it.",
    ).flag(default = false)

    private val tls: Boolean by option(
        "--tls",
        help = "Use TLS for the Redis connection.",
    ).flag(default = false)

    private val debug: Boolean by option(
        "--debug",
        help = "Enable debug logging to ~/.dioptra/logs/dioptra-debug.log.",
    ).flag(default = false)

    lateinit var parsedOptions: CliOptions
        private set

    init {
        context {
            autoEnvvarPrefix = "DIOPTRA"
        }
    }

    override fun run() {
        parsedOptions = CliOptions(
            connection = CliConnectionOptions(
                url = url,
                profile = profile,
                host = host,
                port = port,
                database = database,
                username = username,
                password = if (passwordRequested) PasswordPrompt.readPassword() else null,
                passwordRequested = passwordRequested,
                tls = tls,
            ),
            debug = debug,
        )
    }
}

fun parseCliOptions(args: Array<String>): CliOptions {
    val command = DioptraCommand()
    command.main(args)
    return command.parsedOptions
}
