package io.github.eyuppastirmaci.dioptra.domain.namespace

data class NamespaceIdentity(
    val displayName: String,
    val normalizedName: String,
    val depth: Int,
    val matcherRule: String?,
)