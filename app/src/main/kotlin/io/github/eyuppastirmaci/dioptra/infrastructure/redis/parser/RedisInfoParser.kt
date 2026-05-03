package io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser

class RedisInfoParser {

    /**
     * Parses Redis INFO output into a structured key-value representation.
     */
    fun parse(rawInfo: String): RedisInfo {
        val values = rawInfo
            .lineSequence()
            .filter { line -> line.isNotBlank() }
            .filterNot { line -> line.startsWith("#") }
            .mapNotNull { line ->
                val separatorIndex = line.indexOf(':')

                if (separatorIndex == -1) {
                    null
                } else {
                    val key = line.substring(0, separatorIndex)
                    val value = line.substring(separatorIndex + 1)

                    key to value
                }
            }
            .toMap()

        return RedisInfo(values)
    }
}

data class RedisInfo(
    val values: Map<String, String>,
) {

    fun string(key: String): String? {
        return values[key]
    }

    fun int(key: String): Int? {
        return values[key]?.toIntOrNull()
    }

    fun long(key: String): Long? {
        return values[key]?.toLongOrNull()
    }

    fun double(key: String): Double? {
        return values[key]?.toDoubleOrNull()
    }
}
