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
    val warning: TextColor,
    val hint: TextColor,
) {

    companion object {

        fun dark(): DioptraTheme {
            return DioptraTheme(
                background = color("#070A12"),
                panel = color("#101624"),
                border = color("#5DA9C9"),
                title = color("#E6EEF7"),
                label = color("#8FA1B7"),
                value = color("#DCE7F3"),
                success = color("#4ADE80"),
                warning = color("#FBBF24"),
                hint = color("#6F7F95"),
            )
        }

        private fun color(value: String): TextColor {
            return TextColor.Factory.fromString(value)
        }
    }
}
