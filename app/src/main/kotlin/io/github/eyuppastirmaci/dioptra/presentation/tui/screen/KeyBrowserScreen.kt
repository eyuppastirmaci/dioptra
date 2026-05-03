package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysRequest
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysUseCase
import io.github.eyuppastirmaci.dioptra.application.key.LoadKeyDetailUseCase
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyBrowserPage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeySummary
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class KeyBrowserScreen(
    private val browseKeysUseCase: BrowseKeysUseCase,
    private val loadKeyDetailUseCase: LoadKeyDetailUseCase,
    initialPattern: String = DEFAULT_PATTERN,
    private val count: Long = DEFAULT_COUNT,
    private val back: (() -> TuiScreen)? = null,
) : TuiScreen {

    private val logger = LoggerFactory.getLogger(KeyBrowserScreen::class.java)
    private val screenScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            DioptraCoroutineExceptionHandler.create(
                logger = logger,
                contextName = "KeyBrowserScreen",
                onError = { exception ->
                    state = KeyBrowserState.Error(UserFacingErrorMessage.from(exception))
                },
            ),
    )

    private var loadingJob: Job? = null
    private var pattern = normalizePattern(initialPattern)
    private var patternInput = pattern
    private var inputMode = InputMode.Browse
    private var selectedKeyIndex = 0
    private var sortMode = KeySortMode.None

    @Volatile
    private var state: KeyBrowserState = KeyBrowserState.Loading(cursor = INITIAL_CURSOR)

    init {
        loadPage(cursor = INITIAL_CURSOR)
    }

    /**
     * Renders the Redis key browser screen using the active TUI context.
     */
    override fun render(context: TuiContext) {
        val panelRect = TuiRect(
            left = 2,
            top = 1,
            width = 84,
            height = 21,
        )

        Panel.draw(
            context = context,
            rect = panelRect,
        )

        drawTitle(context, panelRect)
        drawMetadata(context, panelRect)

        if (inputMode == InputMode.PatternSearch) {
            drawPatternInput(context, panelRect)
        } else {
            when (val currentState = state) {
                is KeyBrowserState.Loading -> {
                    drawLoading(context, panelRect, currentState.cursor)
                }

                is KeyBrowserState.Loaded -> {
                    drawLoadedPage(context, panelRect, currentState.page)
                }

                is KeyBrowserState.Error -> {
                    drawError(context, panelRect, currentState.message)
                }

                is KeyBrowserState.Cancelled -> {
                    drawCancelled(context, panelRect, currentState.cursor)
                }
            }
        }

        drawFooter(context, panelRect)
    }

    /**
     * Handles key browser shortcuts.
     */
    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        if (inputMode == InputMode.PatternSearch) {
            return handlePatternInput(keyStroke)
        }

        return when {
            isEscapeKey(keyStroke) && isLoading() -> {
                cancelLoading()
                TuiScreenResult.Continue
            }

            isBackKey(keyStroke) && back != null -> {
                TuiScreenResult.Navigate(
                    nextScreen = back.invoke(),
                )
            }

            isExitKey(keyStroke) -> {
                TuiScreenResult.Exit
            }

            isCharacter(keyStroke, 'n') -> {
                loadNextPage()
                TuiScreenResult.Continue
            }

            isCharacter(keyStroke, 'r') -> {
                loadPage(cursor = INITIAL_CURSOR)
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.ArrowUp -> {
                moveSelection(delta = -1)
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.ArrowDown -> {
                moveSelection(delta = 1)
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.Enter -> {
                openSelectedKeyDetail()
            }

            isCharacter(keyStroke, 'm') -> {
                applySortMode(KeySortMode.Memory)
                TuiScreenResult.Continue
            }

            isCharacter(keyStroke, 't') -> {
                applySortMode(KeySortMode.Type)
                TuiScreenResult.Continue
            }

            isCharacter(keyStroke, 'l') -> {
                applySortMode(KeySortMode.Ttl)
                TuiScreenResult.Continue
            }

            isCharacter(keyStroke, 'u') -> {
                applySortMode(KeySortMode.None)
                TuiScreenResult.Continue
            }

            isCharacter(keyStroke, '/') -> {
                startPatternInput()
                TuiScreenResult.Continue
            }

            else -> TuiScreenResult.Continue
        }
    }

    override fun close() {
        loadingJob?.cancel()
        screenScope.cancel()
    }

    private fun drawTitle(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 1,
            text = "Dioptra Key Browser",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 2,
            text = "SCAN-based Redis key inspection",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawMetadata(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        val loadedPage = (state as? KeyBrowserState.Loaded)?.page

        val cursorText = if (loadedPage == null) {
            "-"
        } else {
            "${loadedPage.cursor} -> ${loadedPage.nextCursor}"
        }

        val hasMoreText = if (loadedPage?.hasMore == true) "yes" else "no"
        val sortText = "Sort: ${sortMode.label}"

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 4,
            text = "Pattern: $pattern",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 25,
            row = panelRect.top + 4,
            text = "Count: $count",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 42,
            row = panelRect.top + 4,
            text = "Cursor: $cursorText",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 68,
            row = panelRect.top + 4,
            text = "More: $hasMoreText",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 5,
            text = sortText.truncate(76),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawPatternInput(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        if (inputMode != InputMode.PatternSearch) {
            return
        }

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 6,
            text = "Search pattern: ${patternInput.truncate(PATTERN_INPUT_WIDTH)}_".padEnd(78),
            foregroundColor = context.theme.value,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 7,
            text = "Enter: apply   ESC: cancel   empty: *".padEnd(78),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawTableHeader(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        val row = TABLE_HEADER_ROW

        context.putText(
            column = KEY_COLUMN,
            row = row,
            text = "KEY".padEnd(KEY_WIDTH),
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = TYPE_COLUMN,
            row = row,
            text = "TYPE".padEnd(TYPE_WIDTH),
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = TTL_COLUMN,
            row = row,
            text = "TTL".padEnd(TTL_WIDTH),
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = MEMORY_COLUMN,
            row = row,
            text = "MEMORY".padEnd(MEMORY_WIDTH),
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )
    }

    private fun drawLoadedPage(
        context: TuiContext,
        panelRect: TuiRect,
        page: RedisKeyBrowserPage,
    ) {
        if (page.keys.isEmpty()) {
            drawEmptyState(context, panelRect, page)
            return
        }

        val sortedKeys = sortKeys(page.keys)

        drawTableHeader(context, panelRect)
        drawRows(context, sortedKeys, selectedKeyIndex)

        if (!page.hasMore) {
            drawEndOfResults(context, panelRect)
        }
    }

    private fun drawRows(
        context: TuiContext,
        keys: List<RedisKeySummary>,
        selectedIndex: Int,
    ) {
        keys
            .take(MAX_VISIBLE_KEYS)
            .forEachIndexed { index, key ->
                val row = FIRST_KEY_ROW + index
                val selected = index == selectedIndex

                context.putText(
                    column = KEY_COLUMN,
                    row = row,
                    text = formatKeyName(key.name, selected).padEnd(KEY_WIDTH),
                    foregroundColor = if (selected) context.theme.success else context.theme.value,
                    backgroundColor = context.theme.panel,
                    bold = selected,
                )

                context.putText(
                    column = TYPE_COLUMN,
                    row = row,
                    text = key.type.name.lowercase().padEnd(TYPE_WIDTH),
                    foregroundColor = if (selected) context.theme.value else context.theme.label,
                    backgroundColor = context.theme.panel,
                    bold = selected,
                )

                context.putText(
                    column = TTL_COLUMN,
                    row = row,
                    text = formatTtl(key.ttl).padEnd(TTL_WIDTH),
                    foregroundColor = ttlForegroundColor(context, key.ttl, selected),
                    backgroundColor = context.theme.panel,
                    bold = selected || key.ttl == RedisKeyTtlStatus.NoExpiration,
                )

                context.putText(
                    column = MEMORY_COLUMN,
                    row = row,
                    text = formatMemoryUsage(key.memoryUsage).padEnd(MEMORY_WIDTH),
                    foregroundColor = memoryForegroundColor(context, key.memoryUsage, selected),
                    backgroundColor = context.theme.panel,
                    bold = selected || isBigKey(key.memoryUsage),
                )
            }
    }

    private fun drawLoading(
        context: TuiContext,
        panelRect: TuiRect,
        cursor: String,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 8,
            text = "Loading keys...",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 10,
            text = "Scanning cursor $cursor. Press ESC to cancel.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawCancelled(
        context: TuiContext,
        panelRect: TuiRect,
        cursor: String,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 8,
            text = "Scan cancelled at cursor $cursor.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 10,
            text = "Press r to retry, b/ESC to return, or q to exit.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawEmptyState(
        context: TuiContext,
        panelRect: TuiRect,
        page: RedisKeyBrowserPage,
    ) {
        val message = if (page.hasMore) {
            "No keys returned for this scan page."
        } else if (page.pattern == DEFAULT_PATTERN) {
            "No keys found in the selected database."
        } else {
            "No keys match pattern '${page.pattern}'."
        }

        val hint = if (page.hasMore) {
            "Press n to continue scanning from cursor ${page.nextCursor}."
        } else {
            "Press r to refresh, b/ESC to return, or q to exit."
        }

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 8,
            text = message.truncate(76),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 10,
            text = hint.truncate(76),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawEndOfResults(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        val row = FIRST_KEY_ROW + MAX_VISIBLE_KEYS
        context.fillRect(
            rect = TuiRect(
                left = panelRect.left + 1,
                top = row,
                width = panelRect.width - 2,
                height = 1,
            ),
            backgroundColor = context.theme.panel,
        )
        context.putText(
            column = panelRect.left + 3,
            row = row,
            text = "End of keyspace reached.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawError(
        context: TuiContext,
        panelRect: TuiRect,
        message: String,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 8,
            text = "Failed to browse keys:",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 10,
            text = message.truncate(76),
            foregroundColor = context.theme.warning,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 12,
            text = "Press r to retry, b/ESC to return, or q to exit.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawFooter(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        val footerTopRow = panelRect.top + panelRect.height - 3
        val footerBottomRow = panelRect.top + panelRect.height - 2
        clearFooterRow(context, panelRect, footerTopRow)
        clearFooterRow(context, panelRect, footerBottomRow)

        val column = panelRect.left + 3

        when {
            inputMode == InputMode.PatternSearch -> {
                context.putText(
                    column = column,
                    row = footerBottomRow,
                    text = "pattern search mode".truncate(FOOTER_TEXT_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
            }

            isLoading() -> {
                context.putText(
                    column = column,
                    row = footerBottomRow,
                    text = "ESC: cancel scan   q: exit".truncate(FOOTER_TEXT_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
            }

            back != null -> {
                context.putText(
                    column = column,
                    row = footerTopRow,
                    text = "Enter: detail  Up/Down  / search  m/t/l/u sort  n/r".truncate(FOOTER_TEXT_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
                context.putText(
                    column = column,
                    row = footerBottomRow,
                    text = "b/ESC: dashboard".truncate(FOOTER_TEXT_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
            }

            else -> {
                context.putText(
                    column = column,
                    row = footerTopRow,
                    text = "Enter: detail  Up/Down  / search  m/t/l/u sort  n/r".truncate(FOOTER_TEXT_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
                context.putText(
                    column = column,
                    row = footerBottomRow,
                    text = "q/ESC: exit".truncate(FOOTER_TEXT_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
            }
        }
    }

    private fun clearFooterRow(
        context: TuiContext,
        panelRect: TuiRect,
        row: Int,
    ) {
        context.fillRect(
            rect = TuiRect(
                left = panelRect.left + 3,
                top = row,
                width = panelRect.width - 6,
                height = 1,
            ),
            backgroundColor = context.theme.panel,
        )
    }

    private fun handlePatternInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isEscapeKey(keyStroke) -> {
                cancelPatternInput()
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.Enter -> {
                applyPatternInput()
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.Backspace -> {
                patternInput = patternInput.dropLast(1)
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.Character -> {
                appendPatternCharacter(keyStroke.character)
                TuiScreenResult.Continue
            }

            else -> TuiScreenResult.Continue
        }
    }

    private fun startPatternInput() {
        if (isLoading()) {
            cancelLoading()
        }

        patternInput = pattern
        inputMode = InputMode.PatternSearch
    }

    private fun cancelPatternInput() {
        patternInput = pattern
        inputMode = InputMode.Browse
    }

    private fun applyPatternInput() {
        pattern = normalizePattern(patternInput)
        patternInput = pattern
        inputMode = InputMode.Browse
        loadPage(cursor = INITIAL_CURSOR)
    }

    private fun appendPatternCharacter(character: Char?) {
        if (character == null || character.isISOControl()) {
            return
        }

        if (patternInput.length >= MAX_PATTERN_LENGTH) {
            return
        }

        patternInput += character
    }

    private fun normalizePattern(value: String): String {
        return value.trim().ifEmpty { DEFAULT_PATTERN }
    }

    private fun moveSelection(delta: Int) {
        val keys = sortKeys((state as? KeyBrowserState.Loaded)?.page?.keys ?: return)
        if (keys.isEmpty() || isLoading()) {
            return
        }

        val lastVisibleIndex = keys.lastIndex.coerceAtMost(MAX_VISIBLE_KEYS - 1)
        selectedKeyIndex = (selectedKeyIndex + delta).coerceIn(0, lastVisibleIndex)
    }

    private fun openSelectedKeyDetail(): TuiScreenResult {
        val selectedKey = selectedKey() ?: return TuiScreenResult.Continue

        return TuiScreenResult.Navigate(
            KeyDetailScreen(
                key = selectedKey,
                loadKeyDetailUseCase = loadKeyDetailUseCase,
                back = { recreateBrowser() },
            )
        )
    }

    private fun recreateBrowser(): TuiScreen {
        return KeyBrowserScreen(
            browseKeysUseCase = browseKeysUseCase,
            loadKeyDetailUseCase = loadKeyDetailUseCase,
            initialPattern = pattern,
            count = count,
            back = back,
        )
    }

    private fun selectedKey(): RedisKeySummary? {
        val keys = sortKeys((state as? KeyBrowserState.Loaded)?.page?.keys ?: return null)
        if (keys.isEmpty() || selectedKeyIndex !in keys.indices) {
            return null
        }

        return keys[selectedKeyIndex]
    }

    private fun applySortMode(nextSortMode: KeySortMode) {
        if (isLoading()) {
            return
        }

        sortMode = if (sortMode == nextSortMode && nextSortMode != KeySortMode.None) {
            KeySortMode.None
        } else {
            nextSortMode
        }
        selectedKeyIndex = 0
    }

    private fun sortKeys(keys: List<RedisKeySummary>): List<RedisKeySummary> {
        return when (sortMode) {
            KeySortMode.None -> keys
            KeySortMode.Memory -> keys.sortedWith(
                compareByDescending<RedisKeySummary> { key -> memorySortValue(key.memoryUsage) }
                    .thenBy { key -> key.name }
            )
            KeySortMode.Type -> keys.sortedWith(
                compareBy<RedisKeySummary> { key -> key.type.name }
                    .thenBy { key -> key.name }
            )
            KeySortMode.Ttl -> keys.sortedWith(
                compareBy<RedisKeySummary> { key -> ttlSortValue(key.ttl) }
                    .thenBy { key -> key.name }
            )
        }
    }

    private fun memorySortValue(memoryUsage: RedisKeyMemoryUsage): Long {
        return when (memoryUsage) {
            RedisKeyMemoryUsage.Unknown -> -1L
            is RedisKeyMemoryUsage.Known -> memoryUsage.bytes
        }
    }

    private fun ttlSortValue(ttl: RedisKeyTtlStatus): Long {
        return when (ttl) {
            RedisKeyTtlStatus.NoExpiration -> Long.MAX_VALUE
            RedisKeyTtlStatus.KeyDoesNotExist -> Long.MAX_VALUE - 1
            is RedisKeyTtlStatus.Unknown -> Long.MAX_VALUE - 2
            is RedisKeyTtlStatus.Expiring -> ttl.seconds
        }
    }

    private fun loadNextPage() {
        val loadedPage = (state as? KeyBrowserState.Loaded)?.page ?: return

        if (!loadedPage.hasMore || isLoading()) {
            return
        }

        loadPage(cursor = loadedPage.nextCursor)
    }

    private fun loadPage(cursor: String) {
        loadingJob?.cancel()
        selectedKeyIndex = 0

        state = KeyBrowserState.Loading(cursor)

        loadingJob = screenScope.launch {
            try {
                val page = browseKeysUseCase.browse(
                    BrowseKeysRequest(
                        cursor = cursor,
                        pattern = pattern,
                        count = count,
                    )
                )

                selectedKeyIndex = 0
                state = KeyBrowserState.Loaded(page)
            } catch (exception: CancellationException) {
                state = KeyBrowserState.Cancelled(cursor)
            } catch (exception: Exception) {
                logger.error("Failed to browse Redis keys from cursor {}.", cursor, exception)
                state = KeyBrowserState.Error(
                    message = UserFacingErrorMessage.from(exception),
                )
            }
        }
    }

    private fun cancelLoading() {
        val loadingState = state as? KeyBrowserState.Loading ?: return

        loadingJob?.cancel()
        state = KeyBrowserState.Cancelled(loadingState.cursor)
    }

    private fun isLoading(): Boolean {
        return loadingJob?.isActive == true
    }

    private fun formatTtl(ttl: RedisKeyTtlStatus): String {
        return when (ttl) {
            RedisKeyTtlStatus.KeyDoesNotExist -> "missing"
            RedisKeyTtlStatus.NoExpiration -> "! no ttl"
            is RedisKeyTtlStatus.Expiring -> "${ttl.seconds}s"
            is RedisKeyTtlStatus.Unknown -> "unknown(${ttl.rawValue})"
        }
    }

    private fun ttlForegroundColor(
        context: TuiContext,
        ttl: RedisKeyTtlStatus,
        selected: Boolean,
    ): com.googlecode.lanterna.TextColor {
        return when {
            ttl == RedisKeyTtlStatus.NoExpiration -> context.theme.warning
            selected -> context.theme.value
            else -> context.theme.label
        }
    }

    private fun formatMemoryUsage(memoryUsage: RedisKeyMemoryUsage): String {
        return when (memoryUsage) {
            RedisKeyMemoryUsage.Unknown -> "unknown"
            is RedisKeyMemoryUsage.Known -> {
                val formattedBytes = formatBytes(memoryUsage.bytes)
                if (isBigKey(memoryUsage)) {
                    "! $formattedBytes"
                } else {
                    formattedBytes
                }
            }
        }
    }

    private fun memoryForegroundColor(
        context: TuiContext,
        memoryUsage: RedisKeyMemoryUsage,
        selected: Boolean,
    ): com.googlecode.lanterna.TextColor {
        return when {
            isBigKey(memoryUsage) -> context.theme.warning
            selected -> context.theme.value
            else -> context.theme.label
        }
    }

    private fun isBigKey(memoryUsage: RedisKeyMemoryUsage): Boolean {
        return memoryUsage is RedisKeyMemoryUsage.Known &&
            memoryUsage.bytes >= BIG_KEY_THRESHOLD_BYTES
    }

    private fun formatKeyName(
        name: String,
        selected: Boolean,
    ): String {
        val marker = if (selected) "> " else "  "
        return marker + name.truncate(KEY_WIDTH - marker.length)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) {
            return "$bytes B"
        }

        val kilobytes = bytes / 1024.0

        if (kilobytes < 1024) {
            return "%.1f KB".format(kilobytes)
        }

        val megabytes = kilobytes / 1024.0

        return "%.1f MB".format(megabytes)
    }

    private fun isExitKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.EOF || isCharacter(keyStroke, 'q')
    }

    private fun isBackKey(keyStroke: KeyStroke): Boolean {
        return isEscapeKey(keyStroke) || isCharacter(keyStroke, 'b')
    }

    private fun isEscapeKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.Escape
    }

    private fun isCharacter(
        keyStroke: KeyStroke,
        expectedCharacter: Char,
    ): Boolean {
        return keyStroke.keyType == KeyType.Character &&
                keyStroke.character?.lowercaseChar() == expectedCharacter
    }

    private fun String.truncate(maxLength: Int): String {
        return if (length <= maxLength) {
            this
        } else {
            take(maxLength - 1) + "…"
        }
    }

    private sealed interface KeyBrowserState {

        data class Loading(
            val cursor: String,
        ) : KeyBrowserState

        data class Loaded(
            val page: RedisKeyBrowserPage,
        ) : KeyBrowserState

        data class Error(
            val message: String,
        ) : KeyBrowserState

        data class Cancelled(
            val cursor: String,
        ) : KeyBrowserState
    }

    private enum class InputMode {
        Browse,
        PatternSearch,
    }

    private enum class KeySortMode(
        val label: String,
    ) {
        None("none"),
        Memory("memory desc"),
        Type("type asc"),
        Ttl("ttl asc"),
    }

    private companion object {
        const val INITIAL_CURSOR = "0"
        const val DEFAULT_PATTERN = "*"
        const val DEFAULT_COUNT = 20L

        const val MAX_PATTERN_LENGTH = 72
        const val PATTERN_INPUT_WIDTH = 56
        const val BIG_KEY_THRESHOLD_BYTES = 1024L * 1024L
        /** Keeps one spare row below keys for [drawEndOfResults] before the two-line footer. */
        const val MAX_VISIBLE_KEYS = 9
        const val TABLE_HEADER_ROW = 7
        const val FIRST_KEY_ROW = 9

        const val KEY_COLUMN = 5
        const val TYPE_COLUMN = 45
        const val TTL_COLUMN = 57
        const val MEMORY_COLUMN = 68

        const val KEY_WIDTH = 36
        const val TYPE_WIDTH = 8
        const val TTL_WIDTH = 10
        const val MEMORY_WIDTH = 12

        /** Inner footer width so hints stay inside the panel border. */
        const val FOOTER_TEXT_WIDTH = 76
    }
}
