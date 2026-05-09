package io.github.eyuppastirmaci.dioptra.application.safety

data class ProtectedNamespaceRules(
    private val rawRules: List<String> = emptyList(),
) {

    private val rules = rawRules
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val count: Int
        get() = rules.size

    fun firstMatch(keyName: String): ProtectedNamespaceMatch? {
        return rules
            .firstOrNull { rule -> matches(rule, keyName) }
            ?.let { rule -> ProtectedNamespaceMatch(rule = rule, keyName = keyName) }
    }

    private fun matches(
        rule: String,
        keyName: String,
    ): Boolean {
        if (rule == "*") {
            return true
        }

        if (rule.endsWith("*")) {
            return keyName.startsWith(rule.dropLast(1))
        }

        if (rule.endsWith(":")) {
            return keyName.startsWith(rule)
        }

        return keyName == rule
    }
}

data class ProtectedNamespaceMatch(
    val rule: String,
    val keyName: String,
)
