package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.config.HoconConnectionProfileStore
import io.github.eyuppastirmaci.dioptra.config.HoconLastUsedConnectionStore
import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FormInputShortcutTest {

    @Test
    fun `namespace settings text fields accept q and b as input`() {
        var savedSettings: NamespaceAnalysisSettings? = null
        val screen = NamespaceAnalysisSettingsScreen(
            initialSettings = NamespaceAnalysisSettings(),
            saveSettings = { settings ->
                savedSettings = settings
                settings
            },
            saveAvailable = true,
            onSaved = {},
            back = { NoopScreen },
        )

        screen.moveDown(times = 2)
        "queue,billing".forEach { character ->
            assertEquals(TuiScreenResult.Continue, screen.handleInput(characterKey(character)))
        }

        assertIs<TuiScreenResult.Navigate>(screen.handleInput(KeyStroke(KeyType.Enter)))

        assertEquals(listOf("queue", "billing"), savedSettings?.normalizedExpectedNamespaces)
    }

    @Test
    fun `connection form text fields accept q as input before global quit`() {
        val tempDir = createTempDirectory("dioptra-form-input-shortcut-test")
        var attemptedConfig: RedisConnectionConfig? = null
        val screen = ConnectionScreen(
            profileStore = HoconConnectionProfileStore(
                configPath = tempDir.resolve("config.conf"),
            ),
            lastUsedConnectionStore = HoconLastUsedConnectionStore(
                metadataPath = tempDir.resolve("last-used.conf"),
            ),
            connect = { config ->
                attemptedConfig = config
                ConnectionAttemptResult.Failure("not connecting during unit test")
            },
        )

        screen.moveDown(times = 9)
        "queue".forEach { character ->
            assertEquals(TuiScreenResult.Continue, screen.handleInput(characterKey(character)))
        }

        assertEquals(TuiScreenResult.Continue, screen.handleInput(KeyStroke(KeyType.Enter)))

        assertEquals(listOf("queue"), attemptedConfig?.namespaceAnalysisSettings?.normalizedExpectedNamespaces)
    }

    private fun TuiScreen.moveDown(times: Int) {
        repeat(times) {
            handleInput(KeyStroke(KeyType.ArrowDown))
        }
    }

    private fun characterKey(character: Char): KeyStroke {
        return KeyStroke(character, false, false)
    }

    private object NoopScreen : TuiScreen {
        override fun render(context: io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext) = Unit
    }
}
