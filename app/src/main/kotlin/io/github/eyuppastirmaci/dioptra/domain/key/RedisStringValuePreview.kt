package io.github.eyuppastirmaci.dioptra.domain.key

sealed interface RedisStringValuePreview {

    data class Text(
        val value: String,
    ) : RedisStringValuePreview

    data class Json(
        val value: String,
    ) : RedisStringValuePreview

    data class Binary(
        val hexPreview: String,
        val sizeBytes: Int,
        val hexDumpLines: List<String>,
        val hexDumpSampleBytes: Int,
    ) : RedisStringValuePreview
}
