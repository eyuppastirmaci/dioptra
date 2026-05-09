package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceDetailSnapshot

class LoadNamespaceDetailUseCase(
    private val namespaceAnalysisEngine: NamespaceAnalysisEngine,
) {

    suspend fun load(request: LoadNamespaceDetailRequest): NamespaceDetailSnapshot? {
        val computation = namespaceAnalysisEngine.analyze(
            LoadNamespaceAnalysisRequest(
                cursor = request.cursor,
                pattern = request.pattern,
                count = request.count,
                topRiskyNamespaceCount = request.topRiskyNamespaceCount,
            )
        )

        return computation.detailsByNamespace[request.namespaceName.trim().lowercase()]
    }
}

data class LoadNamespaceDetailRequest(
    val namespaceName: String,
    val cursor: String = "0",
    val pattern: String = "*",
    val count: Long = 100L,
    val topRiskyNamespaceCount: Int = 5,
)