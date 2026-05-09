package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings

class NamespaceKeyPatternPolicy(
    settings: NamespaceAnalysisSettings,
) {

    private val allowedPatterns = settings.normalizedAllowedKeyPatterns
    private val ignoredPatterns = settings.normalizedIgnoredKeyPatterns

    fun isIgnored(keyName: String): Boolean {
        return ignoredPatterns.any { pattern -> matches(pattern, keyName) }
    }

    fun isAllowed(keyName: String): Boolean {
        return allowedPatterns.any { pattern -> matches(pattern, keyName) }
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

        if (pattern.endsWith(NAMESPACE_DELIMITER_HINT)) {
            return keyName.startsWith(pattern)
        }

        return keyName == pattern
    }

    private companion object {
        const val WILDCARD = "*"
        const val NAMESPACE_DELIMITER_HINT = ":"
    }
}