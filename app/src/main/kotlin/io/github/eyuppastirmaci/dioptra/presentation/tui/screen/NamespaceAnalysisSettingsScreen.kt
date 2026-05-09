package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage

class NamespaceAnalysisSettingsScreen(
    initialSettings: NamespaceAnalysisSettings,
    private val saveSettings: (NamespaceAnalysisSettings) -> NamespaceAnalysisSettings,
    private val saveAvailable: Boolean,
    private val unavailableMessage: String? = null,
    private val onSaved: (NamespaceAnalysisSettings) -> Unit,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private var activeField = SettingsField.NamespaceDelimiters
    private var message: String? = unavailableMessage
    private var namespaceDelimiters = initialSettings.normalizedDelimiters.joinToString(",")
    private var namespaceDepth = initialSettings.normalizedNamespaceDepth.toString()
    private var expectedNamespaces = initialSettings.normalizedExpectedNamespaces.joinToString(",")
    private var allowedKeyPatterns = initialSettings.normalizedAllowedKeyPatterns.joinToString(",")
    private var ignoredKeyPatterns = initialSettings.normalizedIgnoredKeyPatterns.joinToString(",")
    private var allowWhitespaceInKeys = initialSettings.allowWhitespaceInKeys
    private var allowUppercaseInKeys = initialSettings.allowUppercaseInKeys
    private var allowRepeatedDelimiters = initialSettings.allowRepeatedDelimiters

    override fun render(context: TuiContext) {
        val panelRect = TuiRect(left = 2, top = 1, width = 92, height = 22)
        Panel.draw(context, panelRect)

        context.putText(panelRect.left + 3, panelRect.top + 1, "Namespace Analysis Settings", context.theme.title, context.theme.panel, true)
        context.putText(panelRect.left + 3, panelRect.top + 2, "Update and persist analysis rules for this connection profile", context.theme.hint, context.theme.panel)
        if (!saveAvailable) {
            context.putText(
                panelRect.left + 3,
                panelRect.top + 3,
                (unavailableMessage ?: "Persistent save unavailable for this session.").take(panelRect.width - 6),
                context.theme.warning,
                context.theme.panel,
            )
        }

        val left = panelRect.left + 3
        val top = panelRect.top + 6
        drawField(context, left, top + 0, SettingsField.NamespaceDelimiters, "Delimiters", namespaceDelimiters.ifBlank { ":" })
        drawField(context, left, top + 1, SettingsField.NamespaceDepth, "NS Depth", namespaceDepth)
        drawField(context, left, top + 2, SettingsField.ExpectedNamespaces, "Expected", expectedNamespaces.ifBlank { "-" })
        drawField(context, left, top + 3, SettingsField.AllowedKeyPatterns, "Allowed", allowedKeyPatterns.ifBlank { "-" })
        drawField(context, left, top + 4, SettingsField.IgnoredKeyPatterns, "Ignored", ignoredKeyPatterns.ifBlank { "-" })
        drawField(context, left, top + 5, SettingsField.AllowWhitespace, "Whitespace", if (allowWhitespaceInKeys) "allowed" else "flag anomaly")
        drawField(context, left, top + 6, SettingsField.AllowUppercase, "Uppercase", if (allowUppercaseInKeys) "allowed" else "flag anomaly")
        drawField(context, left, top + 7, SettingsField.AllowRepeatedDelimiters, "Repeat delim", if (allowRepeatedDelimiters) "allowed" else "flag anomaly")

        message?.let {
            context.putText(panelRect.left + 3, panelRect.top + panelRect.height - 5, it.take(panelRect.width - 6), context.theme.hint, context.theme.panel)
        }

        context.putText(
            panelRect.left + 3,
            panelRect.top + panelRect.height - 2,
            "Enter:save  Space:toggle  Arrows/Tab:fields  b/esc:back  q:exit".take(panelRect.width - 6),
            context.theme.hint,
            context.theme.panel,
        )
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isExitKey(keyStroke) -> TuiScreenResult.Exit
            isBackKey(keyStroke) -> TuiScreenResult.Navigate(back())
            keyStroke.keyType == KeyType.ArrowUp -> {
                activeField = activeField.previous()
                TuiScreenResult.Continue
            }
            keyStroke.keyType == KeyType.ArrowDown || keyStroke.keyType == KeyType.Tab -> {
                activeField = activeField.next()
                TuiScreenResult.Continue
            }
            keyStroke.keyType == KeyType.Backspace -> {
                deleteLastCharacter()
                TuiScreenResult.Continue
            }
            keyStroke.keyType == KeyType.Enter -> save()
            isCharacter(keyStroke, ' ') && activeField == SettingsField.AllowWhitespace -> {
                allowWhitespaceInKeys = !allowWhitespaceInKeys
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, ' ') && activeField == SettingsField.AllowUppercase -> {
                allowUppercaseInKeys = !allowUppercaseInKeys
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, ' ') && activeField == SettingsField.AllowRepeatedDelimiters -> {
                allowRepeatedDelimiters = !allowRepeatedDelimiters
                TuiScreenResult.Continue
            }
            keyStroke.keyType == KeyType.Character -> {
                appendCharacter(keyStroke.character)
                TuiScreenResult.Continue
            }
            else -> TuiScreenResult.Continue
        }
    }

    private fun save(): TuiScreenResult {
        if (!saveAvailable) {
            message = unavailableMessage ?: "Persistent save unavailable for this session."
            return TuiScreenResult.Continue
        }

        return runCatching {
            val settings = buildSettings()
            val saved = saveSettings(settings)
            onSaved(saved)
            TuiScreenResult.Navigate(back())
        }.getOrElse { exception ->
            message = UserFacingErrorMessage.from(exception)
            TuiScreenResult.Continue
        }
    }

    private fun buildSettings(): NamespaceAnalysisSettings {
        return NamespaceAnalysisSettings(
            delimiters = parseDelimitedValues(namespaceDelimiters).ifEmpty { listOf(":") },
            namespaceDepth = namespaceDepth.toIntOrNull() ?: 1,
            expectedNamespaces = parseDelimitedValues(expectedNamespaces),
            allowedKeyPatterns = parseDelimitedValues(allowedKeyPatterns),
            ignoredKeyPatterns = parseDelimitedValues(ignoredKeyPatterns),
            allowWhitespaceInKeys = allowWhitespaceInKeys,
            allowUppercaseInKeys = allowUppercaseInKeys,
            allowRepeatedDelimiters = allowRepeatedDelimiters,
        )
    }

    private fun drawField(
        context: TuiContext,
        left: Int,
        row: Int,
        field: SettingsField,
        label: String,
        value: String,
    ) {
        val selected = activeField == field
        val prefix = if (selected) "> " else "  "
        context.putText(
            left,
            row,
            "$prefix${label.padEnd(10)} $value".take(84),
            if (selected) context.theme.value else context.theme.label,
            context.theme.panel,
        )
    }

    private fun appendCharacter(character: Char?) {
        val value = character ?: return
        when (activeField) {
            SettingsField.NamespaceDelimiters -> namespaceDelimiters += value
            SettingsField.NamespaceDepth -> if (value.isDigit()) namespaceDepth += value
            SettingsField.ExpectedNamespaces -> expectedNamespaces += value
            SettingsField.AllowedKeyPatterns -> allowedKeyPatterns += value
            SettingsField.IgnoredKeyPatterns -> ignoredKeyPatterns += value
            SettingsField.AllowWhitespace,
            SettingsField.AllowUppercase,
            SettingsField.AllowRepeatedDelimiters -> Unit
        }
    }

    private fun deleteLastCharacter() {
        when (activeField) {
            SettingsField.NamespaceDelimiters -> namespaceDelimiters = namespaceDelimiters.dropLast(1)
            SettingsField.NamespaceDepth -> namespaceDepth = namespaceDepth.dropLast(1)
            SettingsField.ExpectedNamespaces -> expectedNamespaces = expectedNamespaces.dropLast(1)
            SettingsField.AllowedKeyPatterns -> allowedKeyPatterns = allowedKeyPatterns.dropLast(1)
            SettingsField.IgnoredKeyPatterns -> ignoredKeyPatterns = ignoredKeyPatterns.dropLast(1)
            SettingsField.AllowWhitespace,
            SettingsField.AllowUppercase,
            SettingsField.AllowRepeatedDelimiters -> Unit
        }
    }

    private fun parseDelimitedValues(value: String): List<String> {
        return value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun isExitKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.EOF || isCharacter(keyStroke, 'q')
    }

    private fun isBackKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.Escape || isCharacter(keyStroke, 'b')
    }

    private fun isCharacter(
        keyStroke: KeyStroke,
        expectedCharacter: Char,
    ): Boolean {
        return keyStroke.keyType == KeyType.Character && keyStroke.character?.lowercaseChar() == expectedCharacter
    }

    private enum class SettingsField {
        NamespaceDelimiters,
        NamespaceDepth,
        ExpectedNamespaces,
        AllowedKeyPatterns,
        IgnoredKeyPatterns,
        AllowWhitespace,
        AllowUppercase,
        AllowRepeatedDelimiters;

        fun next(): SettingsField = entries[(ordinal + 1) % entries.size]

        fun previous(): SettingsField = entries[(ordinal - 1 + entries.size) % entries.size]
    }
}