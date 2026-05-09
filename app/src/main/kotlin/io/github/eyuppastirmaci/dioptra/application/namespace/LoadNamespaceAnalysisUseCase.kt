package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceAnalysisSnapshot

class LoadNamespaceAnalysisUseCase(
    private val namespaceAnalysisEngine: NamespaceAnalysisEngine,
) {

    suspend fun load(
        request: LoadNamespaceAnalysisRequest = LoadNamespaceAnalysisRequest(),
    ): NamespaceAnalysisSnapshot {
        return namespaceAnalysisEngine.analyze(request).snapshot
    }
}