package io.github.eyuppastirmaci.dioptra.presentation.tui.component

import com.googlecode.lanterna.TextColor
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext

object MetricRow {

    fun draw(
        context: TuiContext,
        row: Int,
        label: String,
        value: String,
        labelColumn: Int = 7,
        valueColumn: Int = 30,
        valueForegroundColor: TextColor = context.theme.value,
    ) {
        context.putText(
            column = labelColumn,
            row = row,
            text = label.padEnd(20),
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = valueColumn,
            row = row,
            text = value,
            foregroundColor = valueForegroundColor,
            backgroundColor = context.theme.panel,
            bold = valueForegroundColor == context.theme.success,
        )
    }
}