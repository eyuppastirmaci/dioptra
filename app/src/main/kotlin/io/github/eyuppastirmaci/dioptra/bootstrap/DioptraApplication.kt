package io.github.eyuppastirmaci.dioptra.bootstrap

import io.github.eyuppastirmaci.dioptra.application.dashboard.LoadDashboardUseCase
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysUseCase
import io.github.eyuppastirmaci.dioptra.presentation.tui.TuiApplication
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.DashboardScreen

class DioptraApplication(
    private val loadDashboardUseCase: LoadDashboardUseCase,
    private val browseKeysUseCase: BrowseKeysUseCase,
    private val tuiApplication: TuiApplication,
) {

    fun run() {
        val dashboardSnapshot = loadDashboardUseCase.load()

        val dashboardScreen = DashboardScreen(
            snapshot = dashboardSnapshot,
            browseKeysUseCase = browseKeysUseCase,
        )

        tuiApplication.run(dashboardScreen)
    }
}