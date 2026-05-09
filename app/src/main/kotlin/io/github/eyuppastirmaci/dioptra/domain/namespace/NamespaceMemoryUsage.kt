package io.github.eyuppastirmaci.dioptra.domain.namespace

sealed interface NamespaceMemoryUsage {

    data object Unknown : NamespaceMemoryUsage

    data class Estimated(
        val totalBytes: Long,
        val sampledKeys: Long? = null,
    ) : NamespaceMemoryUsage
}