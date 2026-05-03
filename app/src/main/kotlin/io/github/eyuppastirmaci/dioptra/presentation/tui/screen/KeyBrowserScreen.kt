package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysRequest
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysUseCase
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyBrowserPage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeySummary
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class KeyBrowserScreen(
    private val browseKeysUseCase: BrowseKeysUseCase,
    private val pattern: String = DEFAULT_PATTERN,
    private val count: Long = DEFAULT_COUNT,
) : TuiScreen {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var loadingJob: Job? = null

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

        when (val currentState = state) {
            is KeyBrowserState.Loading -> {
                drawLoading(context, panelRect, currentState.cursor)
            }

            is KeyBrowserState.Loaded -> {
                drawTableHeader(context, panelRect)
                drawRows(context, panelRect, currentState.page.keys)
            }

            is KeyBrowserState.Error -> {
                drawError(context, panelRect, currentState.message)
            }

            is KeyBrowserState.Cancelled -> {
                drawCancelled(context, panelRect, currentState.cursor)
            }
        }

        drawFooter(context, panelRect)
    }

    /**
     * Handles key browser shortcuts.
     */
    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            isEscapeKey(keyStroke) && isLoading() -> {
                cancelLoading()
                TuiScreenResult.Continue
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
    }

    private fun drawTableHeader(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        val row = panelRect.top + 6

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

    private fun drawRows(
        context: TuiContext,
        panelRect: TuiRect,
        keys: List<RedisKeySummary>,
    ) {
        if (keys.isEmpty()) {
            drawEmptyState(context, panelRect)
            return
        }

        keys
            .take(MAX_VISIBLE_KEYS)
            .forEachIndexed { index, key ->
                val row = panelRect.top + 8 + index

                context.putText(
                    column = KEY_COLUMN,
                    row = row,
                    text = key.name.truncate(KEY_WIDTH).padEnd(KEY_WIDTH),
                    foregroundColor = context.theme.value,
                    backgroundColor = context.theme.panel,
                )

                context.putText(
                    column = TYPE_COLUMN,
                    row = row,
                    text = key.type.name.lowercase().padEnd(TYPE_WIDTH),
                    foregroundColor = context.theme.label,
                    backgroundColor = context.theme.panel,
                )

                context.putText(
                    column = TTL_COLUMN,
                    row = row,
                    text = formatTtl(key.ttl).padEnd(TTL_WIDTH),
                    foregroundColor = context.theme.label,
                    backgroundColor = context.theme.panel,
                )

                context.putText(
                    column = MEMORY_COLUMN,
                    row = row,
                    text = formatMemoryUsage(key.memoryUsage).padEnd(MEMORY_WIDTH),
                    foregroundColor = context.theme.label,
                    backgroundColor = context.theme.panel,
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
            text = "Loading keys from cursor $cursor...",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 10,
            text = "Press ESC to cancel the current scan page.",
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
            text = "Press r to retry or q to exit.",
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawEmptyState(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 8,
            text = "No keys found for the current scan page.",
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
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawFooter(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        val footerText = if (isLoading()) {
            "ESC: cancel scan   q: exit"
        } else {
            "n: next page   r: refresh   q/ESC: exit"
        }

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + panelRect.height - 2,
            text = footerText,
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )
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

                state = KeyBrowserState.Loaded(page)
            } catch (exception: CancellationException) {
                state = KeyBrowserState.Cancelled(cursor)
            } catch (exception: Exception) {
                state = KeyBrowserState.Error(
                    message = exception.message ?: exception::class.simpleName ?: "unknown error",
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
            RedisKeyTtlStatus.NoExpiration -> "no ttl"
            is RedisKeyTtlStatus.Expiring -> "${ttl.seconds}s"
            is RedisKeyTtlStatus.Unknown -> "unknown(${ttl.rawValue})"
        }
    }

    private fun formatMemoryUsage(memoryUsage: RedisKeyMemoryUsage): String {
        return when (memoryUsage) {
            RedisKeyMemoryUsage.Unknown -> "unknown"
            is RedisKeyMemoryUsage.Known -> formatBytes(memoryUsage.bytes)
        }
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
        return keyStroke.keyType == KeyType.EOF ||
                isCharacter(keyStroke, 'q') ||
                isEscapeKey(keyStroke)
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

    private companion object {
        const val INITIAL_CURSOR = "0"
        const val DEFAULT_PATTERN = "*"
        const val DEFAULT_COUNT = 20L

        const val MAX_VISIBLE_KEYS = 10

        const val KEY_COLUMN = 5
        const val TYPE_COLUMN = 45
        const val TTL_COLUMN = 57
        const val MEMORY_COLUMN = 68

        const val KEY_WIDTH = 36
        const val TYPE_WIDTH = 8
        const val TTL_WIDTH = 10
        const val MEMORY_WIDTH = 12
    }
}