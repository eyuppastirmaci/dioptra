package io.github.eyuppastirmaci.dioptra.config

data class RiskAnalysisSettings(
    val bigKeyThresholdBytes: Long = DEFAULT_BIG_KEY_THRESHOLD_BYTES,
    val largeHashFieldThreshold: Long = DEFAULT_LARGE_COLLECTION_THRESHOLD,
    val largeListLengthThreshold: Long = DEFAULT_LARGE_COLLECTION_THRESHOLD,
    val largeSetMemberThreshold: Long = DEFAULT_LARGE_COLLECTION_THRESHOLD,
    val largeZsetMemberThreshold: Long = DEFAULT_LARGE_COLLECTION_THRESHOLD,
    val largeStreamEntryThreshold: Long = DEFAULT_LARGE_COLLECTION_THRESHOLD,
    val topKeyCount: Int = DEFAULT_TOP_KEY_COUNT,
    val scanCount: Long = DEFAULT_SCAN_COUNT,
) {

    val normalizedBigKeyThresholdBytes: Long
        get() = bigKeyThresholdBytes.coerceAtLeast(MIN_THRESHOLD)

    val normalizedLargeHashFieldThreshold: Long
        get() = largeHashFieldThreshold.coerceAtLeast(MIN_THRESHOLD)

    val normalizedLargeListLengthThreshold: Long
        get() = largeListLengthThreshold.coerceAtLeast(MIN_THRESHOLD)

    val normalizedLargeSetMemberThreshold: Long
        get() = largeSetMemberThreshold.coerceAtLeast(MIN_THRESHOLD)

    val normalizedLargeZsetMemberThreshold: Long
        get() = largeZsetMemberThreshold.coerceAtLeast(MIN_THRESHOLD)

    val normalizedLargeStreamEntryThreshold: Long
        get() = largeStreamEntryThreshold.coerceAtLeast(MIN_THRESHOLD)

    val normalizedTopKeyCount: Int
        get() = topKeyCount.coerceAtLeast(MIN_TOP_KEY_COUNT)

    val normalizedScanCount: Long
        get() = scanCount.coerceAtLeast(MIN_SCAN_COUNT)

    private companion object {
        const val DEFAULT_BIG_KEY_THRESHOLD_BYTES = 1_048_576L
        const val DEFAULT_LARGE_COLLECTION_THRESHOLD = 10_000L
        const val DEFAULT_TOP_KEY_COUNT = 20
        const val DEFAULT_SCAN_COUNT = 100L
        const val MIN_THRESHOLD = 1L
        const val MIN_TOP_KEY_COUNT = 1
        const val MIN_SCAN_COUNT = 1L
    }
}
