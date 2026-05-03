package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.config.HoconConnectionProfileStore
import io.github.eyuppastirmaci.dioptra.config.HoconLastUsedConnectionStore
import io.github.eyuppastirmaci.dioptra.config.LastUsedConnectionMetadata
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionProfile
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage

class ConnectionScreen(
    private val profileStore: HoconConnectionProfileStore,
    private val lastUsedConnectionStore: HoconLastUsedConnectionStore,
    private val initialConfig: RedisConnectionConfig = RedisConnectionConfig(),
    private val initialMessage: String? = null,
    private val connect: (RedisConnectionConfig) -> ConnectionAttemptResult,
) : TuiScreen {

    private var profiles = profileStore.load().profiles
    private var mode = if (profiles.isEmpty()) ScreenMode.ConnectionForm else ScreenMode.ProfileList
    private var selectedProfileIndex = 0
    private var activeField = ConnectionField.Host
    private var message = initialMessage

    private var profileName = initialConfig.name
    private var host = initialConfig.host
    private var port = initialConfig.port.toString()
    private var database = initialConfig.database.toString()
    private var username = initialConfig.username.orEmpty()
    private var password = initialConfig.password.orEmpty()
    private var tls = initialConfig.tls
    private var saveConnection = profiles.isEmpty()

    init {
        if (initialConfig == RedisConnectionConfig()) {
            lastUsedConnectionStore.load()?.let(::applyLastUsed)
        }
    }

    override fun render(context: TuiContext) {
        val panelRect = TuiRect(
            left = 2,
            top = 1,
            width = 92,
            height = 25,
        )

        Panel.draw(context, panelRect)

        drawTitle(context, panelRect)
        when (mode) {
            ScreenMode.ProfileList -> drawProfileList(context, panelRect)
            ScreenMode.ConnectionForm -> drawConnectionForm(context, panelRect)
        }
        drawMessage(context, panelRect)
        drawFooter(context, panelRect)
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return when (mode) {
            ScreenMode.ProfileList -> handleProfileListInput(keyStroke)
            ScreenMode.ConnectionForm -> handleConnectionFormInput(keyStroke)
        }
    }

    private fun handleProfileListInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isExitKey(keyStroke) -> TuiScreenResult.Exit
            keyStroke.keyType == KeyType.ArrowUp ||
                keyStroke.keyType == KeyType.PageUp ||
                isCharacter(keyStroke, '[') -> {
                selectPreviousProfile()
                TuiScreenResult.Continue
            }
            keyStroke.keyType == KeyType.ArrowDown ||
                keyStroke.keyType == KeyType.PageDown ||
                isCharacter(keyStroke, ']') -> {
                selectNextProfile()
                TuiScreenResult.Continue
            }
            keyStroke.keyType == KeyType.Enter -> connectSelectedProfile()
            isCharacter(keyStroke, 'n') -> {
                showNewConnectionForm()
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, 'd') -> {
                deleteSelectedProfile()
                TuiScreenResult.Continue
            }
            else -> TuiScreenResult.Continue
        }
    }

    private fun handleConnectionFormInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            keyStroke.keyType == KeyType.Escape && profiles.isNotEmpty() -> {
                showProfileList()
                TuiScreenResult.Continue
            }
            isExitKey(keyStroke) -> TuiScreenResult.Exit
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
            keyStroke.keyType == KeyType.Enter -> connectCurrentForm()
            isCharacter(keyStroke, ' ') && activeField == ConnectionField.Tls -> {
                tls = !tls
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, ' ') && activeField == ConnectionField.SaveConnection -> {
                saveConnection = !saveConnection
                TuiScreenResult.Continue
            }
            isCharacter(keyStroke, 't') -> {
                testCurrentForm()
                TuiScreenResult.Continue
            }
            keyStroke.keyType == KeyType.Character -> {
                appendCharacter(keyStroke.character)
                TuiScreenResult.Continue
            }
            else -> TuiScreenResult.Continue
        }
    }

    private fun drawTitle(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 1,
            text = "Dioptra Redis Connection",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 2,
            text = if (mode == ScreenMode.ProfileList) "Select a saved connection" else "Enter Redis connection details",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawProfileList(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 4,
            text = "Saved Connections",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        profiles.take(MAX_VISIBLE_PROFILES).forEachIndexed { index, profile ->
            val selected = index == selectedProfileIndex
            val prefix = if (selected) "> " else "  "
            val tlsFlag = if (profile.tls) "tls" else "tcp"
            val passwordFlag = if (profile.requiresPassword) ", password" else ""

            context.putText(
                column = panelRect.left + 3,
                row = panelRect.top + 6 + index,
                text = "$prefix${profile.name} (${profile.host}:${profile.port}/db${profile.database}, $tlsFlag$passwordFlag)"
                    .truncate(84),
                foregroundColor = if (selected) context.theme.value else context.theme.hint,
                backgroundColor = context.theme.panel,
            )
        }
    }

    private fun drawConnectionForm(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        val left = panelRect.left + 3
        val top = panelRect.top + 4

        context.putText(
            column = left,
            row = top,
            text = "Connection Details",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        drawField(context, left, top + 2, ConnectionField.ProfileName, "Name", profileName)
        drawField(context, left, top + 3, ConnectionField.Host, "Host", host)
        drawField(context, left, top + 4, ConnectionField.Port, "Port", port)
        drawField(context, left, top + 5, ConnectionField.Database, "Database", database)
        drawField(context, left, top + 6, ConnectionField.Username, "Username", username.ifBlank { "-" })
        drawField(context, left, top + 7, ConnectionField.Password, "Password", password.maskPassword())
        drawField(context, left, top + 8, ConnectionField.Tls, "TLS", if (tls) "on" else "off")
        drawField(
            context = context,
            left = left,
            row = top + 10,
            field = ConnectionField.SaveConnection,
            label = "Save",
            value = if (saveConnection) "[x] save connection without password" else "[ ] do not save",
        )
    }

    private fun drawField(
        context: TuiContext,
        left: Int,
        row: Int,
        field: ConnectionField,
        label: String,
        value: String,
    ) {
        val selected = activeField == field
        val prefix = if (selected) "> " else "  "

        context.putText(
            column = left,
            row = row,
            text = "$prefix${label.padEnd(10)} $value".truncate(84),
            foregroundColor = if (selected) context.theme.value else context.theme.label,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawMessage(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        val safeMessage = message ?: return

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + panelRect.height - 5,
            text = safeMessage.truncate(84),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawFooter(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        val footer = when (mode) {
            ScreenMode.ProfileList -> "Enter connect | n new | d delete | Up/Down select | q/ESC quit"
            ScreenMode.ConnectionForm -> if (profiles.isEmpty()) {
                "Enter connect | t test | Space toggle | Arrows/Tab fields | q/ESC quit"
            } else {
                "Enter connect | t test | Space toggle | Arrows/Tab fields | ESC back | q quit"
            }
        }

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + panelRect.height - 2,
            text = footer.truncate(84),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun connectSelectedProfile(): TuiScreenResult {
        val profile = profiles.getOrNull(selectedProfileIndex) ?: return TuiScreenResult.Continue

        if (profile.requiresPassword) {
            loadProfileIntoForm(profile)
            mode = ScreenMode.ConnectionForm
            activeField = ConnectionField.Password
            saveConnection = false
            message = "Profile '${profile.name}' requires a password."
            return TuiScreenResult.Continue
        }

        return when (val result = connect(profile.toConnectionConfig())) {
            is ConnectionAttemptResult.Success -> TuiScreenResult.Navigate(result.nextScreen)
            is ConnectionAttemptResult.Failure -> {
                message = result.message
                TuiScreenResult.Continue
            }
        }
    }

    private fun connectCurrentForm(): TuiScreenResult {
        val config = buildConfig()

        return when (val result = connect(config)) {
            is ConnectionAttemptResult.Success -> {
                saveProfileAfterSuccessfulConnection(config)
                TuiScreenResult.Navigate(result.nextScreen)
            }
            is ConnectionAttemptResult.Failure -> {
                message = result.message
                TuiScreenResult.Continue
            }
        }
    }

    private fun testCurrentForm() {
        message = when (val result = connect(buildConfig())) {
            is ConnectionAttemptResult.Success -> {
                result.close()
                "Connection test succeeded."
            }
            is ConnectionAttemptResult.Failure -> result.message
        }
    }

    private fun saveProfileAfterSuccessfulConnection(config: RedisConnectionConfig) {
        if (!saveConnection) {
            return
        }

        val profile = RedisConnectionProfile(
            name = config.name,
            host = config.host,
            port = config.port,
            database = config.database,
            username = config.username,
            tls = config.tls,
            timeoutMillis = config.timeoutMillis,
            requiresPassword = password.isNotEmpty(),
        )

        runCatching {
            profileStore.saveProfile(profile)
            profiles = profileStore.load().profiles
        }.onFailure { exception ->
            message = UserFacingErrorMessage.from(exception)
        }
    }

    private fun deleteSelectedProfile() {
        val profile = profiles.getOrNull(selectedProfileIndex) ?: return

        runCatching {
            profileStore.deleteProfile(profile.name)
            profiles = profileStore.load().profiles
            selectedProfileIndex = selectedProfileIndex.coerceAtMost((profiles.size - 1).coerceAtLeast(0))
            if (profiles.isEmpty()) {
                showNewConnectionForm()
            }
            message = "Connection '${profile.name}' deleted."
        }.onFailure { exception ->
            message = UserFacingErrorMessage.from(exception)
        }
    }

    private fun showNewConnectionForm() {
        clearForm()
        mode = ScreenMode.ConnectionForm
        saveConnection = true
        activeField = ConnectionField.Host
        message = "Enter connection details."
    }

    private fun showProfileList() {
        if (profiles.isEmpty()) {
            return
        }

        mode = ScreenMode.ProfileList
        message = null
    }

    private fun clearForm() {
        profileName = "local"
        host = "localhost"
        port = "6379"
        database = "0"
        username = ""
        password = ""
        tls = false
    }

    private fun loadProfileIntoForm(profile: RedisConnectionProfile) {
        profileName = profile.name
        host = profile.host
        port = profile.port.toString()
        database = profile.database.toString()
        username = profile.username.orEmpty()
        password = ""
        tls = profile.tls
    }

    private fun buildConfig(): RedisConnectionConfig {
        return RedisConnectionConfig(
            name = profileName.ifBlank { "local" },
            host = host.ifBlank { "localhost" },
            port = port.toIntOrNull() ?: 6379,
            database = database.toIntOrNull() ?: 0,
            username = username.takeIf { it.isNotBlank() },
            password = password.takeIf { it.isNotEmpty() },
            tls = tls,
        )
    }

    private fun applyLastUsed(metadata: LastUsedConnectionMetadata) {
        profileName = metadata.profileName ?: profileName
        host = metadata.host
        port = metadata.port.toString()
        database = metadata.database.toString()
        username = metadata.username.orEmpty()
        tls = metadata.tls
    }

    private fun appendCharacter(character: Char?) {
        val value = character ?: return

        when (activeField) {
            ConnectionField.ProfileName -> profileName += value
            ConnectionField.Host -> host += value
            ConnectionField.Port -> if (value.isDigit()) port += value
            ConnectionField.Database -> if (value.isDigit()) database += value
            ConnectionField.Username -> username += value
            ConnectionField.Password -> password += value
            ConnectionField.Tls,
            ConnectionField.SaveConnection -> Unit
        }
    }

    private fun deleteLastCharacter() {
        when (activeField) {
            ConnectionField.ProfileName -> profileName = profileName.dropLast(1)
            ConnectionField.Host -> host = host.dropLast(1)
            ConnectionField.Port -> port = port.dropLast(1)
            ConnectionField.Database -> database = database.dropLast(1)
            ConnectionField.Username -> username = username.dropLast(1)
            ConnectionField.Password -> password = password.dropLast(1)
            ConnectionField.Tls,
            ConnectionField.SaveConnection -> Unit
        }
    }

    private fun selectPreviousProfile() {
        if (profiles.isEmpty()) return
        selectedProfileIndex = if (selectedProfileIndex == 0) profiles.lastIndex else selectedProfileIndex - 1
    }

    private fun selectNextProfile() {
        if (profiles.isEmpty()) return
        selectedProfileIndex = if (selectedProfileIndex == profiles.lastIndex) 0 else selectedProfileIndex + 1
    }

    private fun isExitKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.Escape ||
            keyStroke.keyType == KeyType.EOF ||
            isCharacter(keyStroke, 'q')
    }

    private fun isCharacter(
        keyStroke: KeyStroke,
        expectedCharacter: Char,
    ): Boolean {
        return keyStroke.keyType == KeyType.Character &&
            keyStroke.character?.lowercaseChar() == expectedCharacter
    }

    private fun String.maskPassword(): String {
        return if (isEmpty()) "-" else "*".repeat(length.coerceAtMost(12))
    }

    private fun String.truncate(maxLength: Int): String {
        return if (length <= maxLength) this else take(maxLength - 1) + "..."
    }

    private enum class ScreenMode {
        ProfileList,
        ConnectionForm,
    }

    private enum class ConnectionField {
        ProfileName,
        Host,
        Port,
        Database,
        Username,
        Password,
        Tls,
        SaveConnection;

        fun next(): ConnectionField {
            return entries[(ordinal + 1) % entries.size]
        }

        fun previous(): ConnectionField {
            return entries[(ordinal - 1 + entries.size) % entries.size]
        }
    }

    companion object {
        private const val MAX_VISIBLE_PROFILES = 14
    }
}

sealed interface ConnectionAttemptResult {

    data class Success(
        val nextScreen: TuiScreen,
        val close: () -> Unit = {},
    ) : ConnectionAttemptResult

    data class Failure(
        val message: String,
    ) : ConnectionAttemptResult
}
