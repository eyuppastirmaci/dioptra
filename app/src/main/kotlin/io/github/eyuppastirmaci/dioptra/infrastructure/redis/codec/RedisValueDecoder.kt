package io.github.eyuppastirmaci.dioptra.infrastructure.redis.codec

interface RedisValueDecoder {

    fun decode(value: ByteArray): RedisDecodedValue
}

sealed interface RedisDecodedValue {

    data class Text(
        val value: String,
    ) : RedisDecodedValue

    data class Json(
        val value: String,
    ) : RedisDecodedValue

    data class Binary(
        val hexPreview: String,
        val sizeBytes: Int,
        val hexDumpLines: List<String>,
        val hexDumpSampleBytes: Int,
    ) : RedisDecodedValue
}