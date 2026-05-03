package io.github.eyuppastirmaci.dioptra.infrastructure.redis.codec

class JsonValueDetector {

    private val jsonNumberRegex = Regex(
        pattern = """^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$""",
    )

    /**
     * Detects whether UTF-8 text is likely a JSON value (object, array, string, number,
     * or boolean/null literals). Uses delimiter balancing outside quoted regions to reduce
     * false positives such as `{token}` payloads that are not JSON-shaped.
     */
    fun looksLikeJson(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return false
        }

        when (trimmed) {
            "null",
            "true",
            "false",
            -> return true
        }

        if (jsonNumberRegex.matches(trimmed)) {
            return true
        }

        if (looksLikeJsonQuotedString(trimmed)) {
            return true
        }

        if (trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.length >= 2) {
            return hasBalancedJsonDelimiters(trimmed)
        }

        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length >= 2) {
            return hasBalancedJsonDelimiters(trimmed)
        }

        return false
    }

    private fun looksLikeJsonQuotedString(text: String): Boolean {
        if (text.length < 2 || text.first() != '"' || text.last() != '"') {
            return false
        }

        var escape = false
        for (index in 1 until text.lastIndex) {
            val character = text[index]
            when {
                escape -> escape = false
                character == '\\' -> escape = true
                character == '"' -> return false
                else -> Unit
            }
        }

        return true
    }

    private fun hasBalancedJsonDelimiters(text: String): Boolean {
        var objectDepth = 0
        var arrayDepth = 0
        var inString = false
        var escape = false

        for (character in text) {
            if (escape) {
                escape = false
                continue
            }

            if (inString) {
                when (character) {
                    '\\' -> escape = true
                    '"' -> inString = false
                }
                continue
            }

            when (character) {
                '"' -> inString = true
                '{' -> objectDepth++
                '}' -> {
                    if (objectDepth == 0) {
                        return false
                    }
                    objectDepth--
                }

                '[' -> arrayDepth++
                ']' -> {
                    if (arrayDepth == 0) {
                        return false
                    }
                    arrayDepth--
                }
            }
        }

        return !inString && objectDepth == 0 && arrayDepth == 0
    }
}