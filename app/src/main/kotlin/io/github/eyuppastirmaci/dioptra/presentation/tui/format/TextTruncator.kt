package io.github.eyuppastirmaci.dioptra.presentation.tui.format

class TextTruncator(
    private val marker: String = "~",
) {

    fun truncate(
        value: String,
        maxLength: Int,
    ): String {
        return if (value.length <= maxLength) {
            value
        } else {
            value.take(maxLength - marker.length) + marker
        }
    }
}
