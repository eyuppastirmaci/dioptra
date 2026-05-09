package io.github.eyuppastirmaci.dioptra.presentation.tui.format

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

        return "%.1f MB".format(megabytes)
    }

    private companion object {
        const val BYTES_PER_KILOBYTE = 1024L
    }
}
