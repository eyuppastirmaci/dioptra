package io.github.eyuppastirmaci.dioptra.application.format

class ByteSizeFormatter {

    fun format(bytes: Long): String {
        if (bytes < BYTES_PER_KILOBYTE) {
            return "$bytes B"
        }

        val kilobytes = bytes / BYTES_PER_KILOBYTE.toDouble()

        if (kilobytes < BYTES_PER_KILOBYTE) {
            return "%.1f KB".format(kilobytes)
        }

        val megabytes = kilobytes / BYTES_PER_KILOBYTE

        if (megabytes < BYTES_PER_KILOBYTE) {
            return "%.1f MB".format(megabytes)
        }

        val gigabytes = megabytes / BYTES_PER_KILOBYTE
        return "%.2f GB".format(gigabytes)
    }

    private companion object {
        const val BYTES_PER_KILOBYTE = 1024L
    }
}
