package io.github.eyuppastirmaci.dioptra.config

data class NamespaceAnalysisSettings(
    val delimiters: List<String> = listOf(DEFAULT_DELIMITER),
    val namespaceDepth: Int = DEFAULT_NAMESPACE_DEPTH,
    val expectedNamespaces: List<String> = emptyList(),
    val allowedKeyPatterns: List<String> = emptyList(),
    val ignoredKeyPatterns: List<String> = emptyList(),
    val allowWhitespaceInKeys: Boolean = false,
    val allowUppercaseInKeys: Boolean = false,
    val allowRepeatedDelimiters: Boolean = false,
) {

    val normalizedDelimiters: List<String>
        get() = delimiters
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(DEFAULT_DELIMITER) }

    val normalizedExpectedNamespaces: List<String>
        get() = expectedNamespaces
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    val normalizedAllowedKeyPatterns: List<String>
        get() = allowedKeyPatterns
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    val normalizedIgnoredKeyPatterns: List<String>
        get() = ignoredKeyPatterns
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    val normalizedNamespaceDepth: Int
        get() = namespaceDepth.coerceAtLeast(MIN_NAMESPACE_DEPTH)

    private companion object {
        const val DEFAULT_DELIMITER = ":"
        const val DEFAULT_NAMESPACE_DEPTH = 1
        const val MIN_NAMESPACE_DEPTH = 1
    }
}