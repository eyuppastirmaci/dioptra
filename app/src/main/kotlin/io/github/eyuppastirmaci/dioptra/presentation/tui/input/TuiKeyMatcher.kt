package io.github.eyuppastirmaci.dioptra.presentation.tui.input

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType

class TuiKeyMatcher {

    fun isExit(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.EOF || isCharacter(keyStroke, 'q')
    }

    fun isBack(keyStroke: KeyStroke): Boolean {
        return isEscape(keyStroke) || isCharacter(keyStroke, 'b')
    }

    fun isEscape(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.Escape
    }

    fun isCharacter(
        keyStroke: KeyStroke,
        expectedCharacter: Char,
    ): Boolean {
        return keyStroke.keyType == KeyType.Character &&
            keyStroke.character?.lowercaseChar() == expectedCharacter
    }
}
