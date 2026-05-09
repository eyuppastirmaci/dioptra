package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysRequest
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysUseCase
import io.github.eyuppastirmaci.dioptra.application.key.LoadKeyDetailUseCase
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeySummary
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeyBrowserSorter
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeySortMode
import io.github.eyuppastirmaci.dioptra.presentation.tui.input.TuiKeyMatcher
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser.KeyBrowserInputMode
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser.KeyBrowserRenderState
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser.KeyBrowserRenderer
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser.KeyBrowserState
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
    private val renderer: KeyBrowserRenderer,
    private val sorter: RedisKeyBrowserSorter,
    private val keyMatcher: TuiKeyMatcher,
    initialPattern: String = KeyBrowserRenderer.DEFAULT_PATTERN,
    private val count: Long = KeyBrowserRenderer.DEFAULT_COUNT,
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
    private var inputMode = KeyBrowserInputMode.Browse
    private var selectedKeyIndex = 0
    private var sortMode = RedisKeySortMode.None

    @Volatile
    private var state: KeyBrowserState = KeyBrowserState.Loading(cursor = KeyBrowserRenderer.INITIAL_CURSOR)

    init {
        loadPage(cursor = KeyBrowserRenderer.INITIAL_CURSOR)
    }

    override fun render(context: TuiContext) {
        renderer.render(
            context = context,
            renderState = KeyBrowserRenderState(
                state = state,
                pattern = pattern,
                patternInput = patternInput,
                inputMode = inputMode,
                selectedKeyIndex = selectedKeyIndex,
                sortMode = sortMode,
                count = count,
                isLoading = isLoading(),
                canReturnBack = back != null,
            ),
        )
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        if (inputMode == KeyBrowserInputMode.PatternSearch) {
            return handlePatternInput(keyStroke)
        }

        return when {
            keyMatcher.isEscape(keyStroke) && isLoading() -> {
                cancelLoading()
                TuiScreenResult.Continue
            }

            keyMatcher.isBack(keyStroke) && back != null -> {
                TuiScreenResult.Navigate(nextScreen = back.invoke())
            }

            keyMatcher.isExit(keyStroke) -> TuiScreenResult.Exit

            keyMatcher.isCharacter(keyStroke, 'n') -> {
                loadNextPage()
                TuiScreenResult.Continue
            }

            keyMatcher.isCharacter(keyStroke, 'r') -> {
                loadPage(cursor = KeyBrowserRenderer.INITIAL_CURSOR)
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

            keyStroke.keyType == KeyType.Enter -> openSelectedKeyDetail()

            keyMatcher.isCharacter(keyStroke, 'm') -> {
                applySortMode(RedisKeySortMode.Memory)
                TuiScreenResult.Continue
            }

            keyMatcher.isCharacter(keyStroke, 't') -> {
                applySortMode(RedisKeySortMode.Type)
                TuiScreenResult.Continue
            }

            keyMatcher.isCharacter(keyStroke, 'l') -> {
                applySortMode(RedisKeySortMode.Ttl)
                TuiScreenResult.Continue
            }

            keyMatcher.isCharacter(keyStroke, 'u') -> {
                applySortMode(RedisKeySortMode.None)
                TuiScreenResult.Continue
            }

            keyMatcher.isCharacter(keyStroke, '/') -> {
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

    private fun handlePatternInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            keyMatcher.isEscape(keyStroke) -> {
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
        inputMode = KeyBrowserInputMode.PatternSearch
    }

    private fun cancelPatternInput() {
        patternInput = pattern
        inputMode = KeyBrowserInputMode.Browse
    }

    private fun applyPatternInput() {
        pattern = normalizePattern(patternInput)
        patternInput = pattern
        inputMode = KeyBrowserInputMode.Browse
        loadPage(cursor = KeyBrowserRenderer.INITIAL_CURSOR)
    }

    private fun appendPatternCharacter(character: Char?) {
        if (character == null || character.isISOControl()) {
            return
        }

        if (patternInput.length >= KeyBrowserRenderer.MAX_PATTERN_LENGTH) {
            return
        }

        patternInput += character
    }

    private fun normalizePattern(value: String): String {
        return value.trim().ifEmpty { KeyBrowserRenderer.DEFAULT_PATTERN }
    }

    private fun moveSelection(delta: Int) {
        val keys = sortedLoadedKeys() ?: return
        if (keys.isEmpty() || isLoading()) {
            return
        }

        val lastVisibleIndex = keys.lastIndex.coerceAtMost(KeyBrowserRenderer.MAX_VISIBLE_KEYS - 1)
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
            renderer = renderer,
            sorter = sorter,
            keyMatcher = keyMatcher,
            initialPattern = pattern,
            count = count,
            back = back,
        )
    }

    private fun selectedKey(): RedisKeySummary? {
        val keys = sortedLoadedKeys() ?: return null
        if (keys.isEmpty() || selectedKeyIndex !in keys.indices) {
            return null
        }

        return keys[selectedKeyIndex]
    }

    private fun sortedLoadedKeys(): List<RedisKeySummary>? {
        val keys = (state as? KeyBrowserState.Loaded)?.page?.keys ?: return null
        return sorter.sort(keys, sortMode)
    }

    private fun applySortMode(nextSortMode: RedisKeySortMode) {
        if (isLoading()) {
            return
        }

        sortMode = if (sortMode == nextSortMode && nextSortMode != RedisKeySortMode.None) {
            RedisKeySortMode.None
        } else {
            nextSortMode
        }
        selectedKeyIndex = 0
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
}
