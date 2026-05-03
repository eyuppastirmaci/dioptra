package io.github.eyuppastirmaci.dioptra.bootstrap

import io.github.eyuppastirmaci.dioptra.cli.CliOptions

/**
 * Thin entry facade. All Redis/TUI wiring lives in [ApplicationBootstrap],
 * including key detail use case wiring for dashboard → key browser → key detail.
 */
class DioptraApplication {

    fun start(cliOptions: CliOptions = CliOptions()) {
        ApplicationBootstrap().start(cliOptions)
    }
}
