package io.github.eyuppastirmaci.dioptra.application.session

import io.github.eyuppastirmaci.dioptra.application.safety.OperationAuditResult
import java.time.Duration
import java.time.Instant

enum class TrackedScreen(val label: String) {
    KEY_BROWSER("Key Browser"),
    SLOWLOG("Slowlog"),
    COMMAND_STATS("Command Stats"),
    LATENCY_STATS("Latency Stats"),
    RISK_ANALYSIS("Risk Analysis"),
    NAMESPACE_ANALYSIS("Namespace Analysis"),
}

data class SessionSummary(
    val profileName: String,
    val maskedUri: String,
    val database: Int,
    val readOnly: Boolean,
    val productionSafety: Boolean,
    val duration: Duration,
    val screenVisits: List<TrackedScreen>,
    val keysBrowsedCount: Int,
    val keysBrowsedPageCount: Int,
    val keysInspectedCount: Int,
    val operationResults: Map<String, Map<OperationAuditResult, Int>>,
) {
    val hasConnection: Boolean get() = maskedUri.isNotBlank()
}

class SessionTracker {

    val startedAt: Instant = Instant.now()

    var profileName: String = ""
        private set
    var maskedUri: String = ""
        private set
    var database: Int = 0
        private set
    var readOnly: Boolean = false
        private set
    var productionSafety: Boolean = false
        private set

    private val visitedScreens = mutableListOf<TrackedScreen>()
    private var keysBrowsedCount = 0
    private var keysBrowsedPageCount = 0
    private var keysInspectedCount = 0
    private val operationResults = mutableMapOf<String, MutableMap<OperationAuditResult, Int>>()

    fun setConnectionInfo(
        profileName: String,
        maskedUri: String,
        database: Int,
        readOnly: Boolean,
        productionSafety: Boolean,
    ) {
        this.profileName = profileName
        this.maskedUri = maskedUri
        this.database = database
        this.readOnly = readOnly
        this.productionSafety = productionSafety
    }

    fun recordScreenVisit(screen: TrackedScreen) {
        if (visitedScreens.lastOrNull() != screen) {
            visitedScreens.add(screen)
        }
    }

    fun recordKeysBrowsed(keyCount: Int) {
        keysBrowsedCount += keyCount
        keysBrowsedPageCount++
    }

    fun recordKeyInspected() {
        keysInspectedCount++
    }

    fun recordOperation(action: String, result: OperationAuditResult) {
        operationResults
            .getOrPut(action) { mutableMapOf() }
            .merge(result, 1, Int::plus)
    }

    fun snapshot(): SessionSummary {
        val duration = Duration.between(startedAt, Instant.now())
        val deduplicatedVisits = visitedScreens.distinct()
        return SessionSummary(
            profileName = profileName,
            maskedUri = maskedUri,
            database = database,
            readOnly = readOnly,
            productionSafety = productionSafety,
            duration = duration,
            screenVisits = deduplicatedVisits,
            keysBrowsedCount = keysBrowsedCount,
            keysBrowsedPageCount = keysBrowsedPageCount,
            keysInspectedCount = keysInspectedCount,
            operationResults = operationResults.mapValues { it.value.toMap() },
        )
    }
}
