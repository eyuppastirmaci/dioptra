package io.github.eyuppastirmaci.dioptra

import io.github.eyuppastirmaci.dioptra.bootstrap.DioptraApplication
import io.github.eyuppastirmaci.dioptra.cli.parseCliOptions
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val cliOptions = parseCliOptions(args)
    configureLogging(cliOptions.debug)
    val logger = LoggerFactory.getLogger("io.github.eyuppastirmaci.dioptra.App")

    try {
        logger.info("Starting Dioptra.")
        DioptraApplication().start(cliOptions)
        logger.info("Dioptra stopped.")
    } catch (exception: Exception) {
        logger.error("Dioptra failed to start.", exception)
        exitProcess(1)
    }
}

private fun configureLogging(debug: Boolean) {
    if (debug) {
        System.setProperty("DIOPTRA_LOG_LEVEL", "DEBUG")
    }
}
