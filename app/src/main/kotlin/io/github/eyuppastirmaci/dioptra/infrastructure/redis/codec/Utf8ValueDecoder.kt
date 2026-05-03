package io.github.eyuppastirmaci.dioptra.infrastructure.redis.codec

import java.nio.charset.StandardCharsets

class Utf8ValueDecoder(
    private val jsonValueDetector: JsonValueDetector = JsonValueDetector(),
    private val binaryValueDetector: BinaryValueDetector = BinaryValueDetector(),
) : RedisValueDecoder {

    /**
     * Decodes Redis raw bytes into text, JSON-like text, or binary preview.
     */
    override fun decode(value: ByteArray): RedisDecodedValue {
        if (binaryValueDetector.isBinary(value)) {
            return RedisDecodedValue.Binary(
                hexPreview = value.toHexPreview(),
                sizeBytes = value.size,
            )
        }

        val text = value.toString(StandardCharsets.UTF_8)

        return if (jsonValueDetector.looksLikeJson(text)) {
            RedisDecodedValue.Json(text)
        } else {
            RedisDecodedValue.Text(text)
        }
    }

    private fun ByteArray.toHexPreview(limit: Int = 64): String {
        return take(limit)
            .joinToString(separator = " ") { byte ->
                byte.toUByte().toString(radix = 16).padStart(2, '0')
            }
    }
}