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
            val sampleEnd = minOf(MAX_HEX_DUMP_SAMPLE_BYTES, value.size)
            val sample = value.copyOfRange(fromIndex = 0, toIndex = sampleEnd)
            return RedisDecodedValue.Binary(
                hexPreview = value.toHexPreview(),
                sizeBytes = value.size,
                hexDumpLines = HexDumpFormatter.lines(sample),
                hexDumpSampleBytes = sample.size,
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

    private companion object {
        const val MAX_HEX_DUMP_SAMPLE_BYTES = 128
    }
}