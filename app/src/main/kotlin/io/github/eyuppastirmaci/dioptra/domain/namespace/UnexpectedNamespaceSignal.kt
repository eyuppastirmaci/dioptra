package io.github.eyuppastirmaci.dioptra.domain.namespace

data class UnexpectedNamespaceSignal(
    val namespaceName: String,
    val reason: String,
)