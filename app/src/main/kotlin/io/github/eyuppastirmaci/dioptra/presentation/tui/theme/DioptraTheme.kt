package io.github.eyuppastirmaci.dioptra.presentation.tui.theme

import com.googlecode.lanterna.TextColor

data class DioptraTheme(
    val background: TextColor,
    val panel: TextColor,
    val border: TextColor,
    val title: TextColor,
    val label: TextColor,
    val value: TextColor,
    val success: TextColor,
    val hint: TextColor,
) {

    companion object {

        fun dark(): DioptraTheme {
            return DioptraTheme(
                background = color("#0B1020"),
                panel = color("#111827"),
                border = color("#38BDF8"),
                title = color("#E0F2FE"),
                label = color("#94A3B8"),
                value = color("#F8FAFC"),
                success = color("#22C55E"),
                hint = color("#64748B"),
            )
        }

        private fun color(value: String): TextColor {
            return TextColor.Factory.fromString(value)
        }
    }
}