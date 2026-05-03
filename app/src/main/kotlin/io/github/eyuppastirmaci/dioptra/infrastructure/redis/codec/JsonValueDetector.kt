package io.github.eyuppastirmaci.dioptra.infrastructure.redis.codec

class JsonValueDetector {

    /**
     * Detects whether a string looks like a JSON object or array.
     */
    fun looksLikeJson(value: String): Boolean {
        val trimmedValue = value.trim()

        return trimmedValue.length >= 2 && (
                        trimmedValue.startsWith("{") && trimmedValue.endsWith("}") ||
                        trimmedValue.startsWith("[") &&
                        trimmedValue.endsWith("]")
                )
    }
}