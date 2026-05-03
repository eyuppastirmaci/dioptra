package io.github.eyuppastirmaci.dioptra

import io.github.eyuppastirmaci.dioptra.bootstrap.ApplicationBootstrap

fun main() {
    try {

        // Boots the application by wiring dependencies and starting the main flow.
        ApplicationBootstrap().start()

    } catch (exception: Exception) {
        System.err.println("Dioptra failed to start.")
        System.err.println("Reason: ${exception.message}")
        exception.printStackTrace()
    }
}