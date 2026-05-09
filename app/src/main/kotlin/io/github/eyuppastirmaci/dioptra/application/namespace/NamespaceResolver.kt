package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.domain.namespace.NamespaceIdentity
import java.util.regex.Pattern

data class NamespaceResolutionRule(
    val pattern: String,
    val namespaceName: String? = null,
    val depth: Int = 1,
)

class NamespaceResolver(
    private val settings: NamespaceAnalysisSettings = NamespaceAnalysisSettings(),
    private val rules: List<NamespaceResolutionRule> = emptyList(),
) {

    private val delimiters = settings.normalizedDelimiters
    private val primaryDelimiter = delimiters.first()
    private val splitRegex = delimiters
        .joinToString(separator = "|") { Pattern.quote(it) }
        .toRegex()

    val hasExpectations: Boolean
        get() = rules.isNotEmpty() || settings.normalizedExpectedNamespaces.isNotEmpty()

    fun resolve(keyName: String): NamespaceIdentity {
        val normalizedKeyName = keyName.trim()
        if (normalizedKeyName.isEmpty()) {
            return NamespaceIdentity(
                displayName = ROOT_NAMESPACE,
                normalizedName = ROOT_NAMESPACE,
                depth = 0,
                matcherRule = null,
            )
        }

        val matchedRule = rules.firstOrNull { rule -> matches(rule.pattern, normalizedKeyName) }
        val namespaceName = when {
            matchedRule?.namespaceName != null -> matchedRule.namespaceName
            else -> deriveNamespaceName(
                keyName = normalizedKeyName,
                depth = matchedRule?.depth ?: settings.normalizedNamespaceDepth,
            )
        }

        return NamespaceIdentity(
            displayName = namespaceName,
            normalizedName = namespaceName.lowercase(),
            depth = namespaceDepth(namespaceName),
            matcherRule = matchedRule?.pattern,
        )
    }

    fun isUnexpected(identity: NamespaceIdentity): Boolean {
        if (!hasExpectations) {
            return false
        }

        if (identity.matcherRule != null) {
            return false
        }

        val normalizedExpectedNamespaces = settings.normalizedExpectedNamespaces.map { it.lowercase() }
        if (normalizedExpectedNamespaces.isEmpty()) {
            return false
        }

        return identity.displayName.lowercase() !in normalizedExpectedNamespaces
    }

    fun splitSegments(value: String): List<String> {
        return value
            .split(splitRegex)
            .filter { it.isNotBlank() }
    }

    fun containsRepeatedDelimiter(value: String): Boolean {
        return delimiters.any { delimiter -> value.contains(delimiter.repeat(2)) }
    }

    private fun deriveNamespaceName(
        keyName: String,
        depth: Int,
    ): String {
        val effectiveDepth = depth.coerceAtLeast(1)
        val segments = splitSegments(keyName)

        if (segments.isEmpty()) {
            return ROOT_NAMESPACE
        }

        return segments
            .take(effectiveDepth)
            .joinToString(separator = primaryDelimiter)
    }

    private fun namespaceDepth(namespaceName: String): Int {
        return splitSegments(namespaceName).size
    }

    private fun matches(
        pattern: String,
        keyName: String,
    ): Boolean {
        if (pattern == WILDCARD) {
            return true
        }

        if (pattern.endsWith(WILDCARD)) {
            return keyName.startsWith(pattern.dropLast(1))
        }

        if (delimiters.any { delimiter -> pattern.endsWith(delimiter) }) {
            return keyName.startsWith(pattern)
        }

        return keyName == pattern
    }

    private companion object {
        const val ROOT_NAMESPACE = "(root)"
        const val WILDCARD = "*"
    }
}