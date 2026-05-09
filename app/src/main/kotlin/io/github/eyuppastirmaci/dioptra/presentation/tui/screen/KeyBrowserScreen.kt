package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysRequest
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysUseCase
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyRequest
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyResult
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyUseCase
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyValueUseCase
import io.github.eyuppastirmaci.dioptra.application.key.ExpireKeyUseCase
import io.github.eyuppastirmaci.dioptra.application.key.LoadKeyDetailUseCase
import io.github.eyuppastirmaci.dioptra.application.safety.OperationAuditLogger
import io.github.eyuppastirmaci.dioptra.application.safety.OperationAuditResult
import io.github.eyuppastirmaci.dioptra.application.safety.ProtectedNamespaceRules
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeySummary
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.SafeOperationErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeyBrowserSorter
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeySortMode
import io.github.eyuppastirmaci.dioptra.presentation.tui.input.TuiKeyMatcher
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser.KeyBrowserInputMode
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser.KeyBrowserRenderState
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser.KeyBrowserRenderer
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser.KeyBrowserState
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keyoperation.KeyOperationMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keyoperation.KeyOperationToast
import io.github.eyuppastirmaci.dioptra.presentation.tui.ttl.LiveTtlTracker
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
    private val expireKeyUseCase: ExpireKeyUseCase,
    private val deleteKeyUseCase: DeleteKeyUseCase,
    private val deleteKeyValueUseCase: DeleteKeyValueUseCase,
    private val readOnly: Boolean,
    private val productionSafety: Boolean,
    private val protectedNamespaceRules: ProtectedNamespaceRules,
    private val operationAuditLogger: OperationAuditLogger,
    private val renderer: KeyBrowserRenderer,
    private val sorter: RedisKeyBrowserSorter,
    private val keyMatcher: TuiKeyMatcher,
    initialPattern: String = KeyBrowserRenderer.DEFAULT_PATTERN,
    private val count: Long = KeyBrowserRenderer.DEFAULT_COUNT,
    private val back: (() -> TuiScreen)? = null,
) : TuiScreen {

    private val logger = LoggerFactory.getLogger(KeyBrowserScreen::class.java)
    private val liveTtlTracker = LiveTtlTracker()
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
    private var deleteJob: Job? = null
    private var pattern = normalizePattern(initialPattern)
    private var patternInput = pattern
    private var inputMode = KeyBrowserInputMode.Browse
    private var selectedKeyIndex = 0
    private var sortMode = RedisKeySortMode.None
    private var pendingDeleteKey: RedisKeySummary? = null
    private var productionDeleteAcknowledged = false

    @Volatile
    private var operationToast: KeyOperationToast? = null

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
                productionSafety = productionSafety,
                operationToast = currentOperationToast(),
                liveTtlDisplay = { key -> liveTtlTracker.display(key.name, key.ttl) },
            ),
        )
    }

    override fun tick(): TuiScreenResult {
        val loaded = state as? KeyBrowserState.Loaded ?: return TuiScreenResult.Continue
        val removableNames = loaded.page.keys
            .filter { key -> liveTtlTracker.expiredGraceComplete(key.name, key.ttl, EXPIRED_GRACE_MILLIS) }
            .mapTo(mutableSetOf()) { it.name }

        if (removableNames.isEmpty()) {
            return TuiScreenResult.Continue
        }

        val remainingKeys = loaded.page.keys.filterNot { it.name in removableNames }
        selectedKeyIndex = selectedKeyIndex.coerceAtMost((remainingKeys.size - 1).coerceAtLeast(0))
        liveTtlTracker.forgetAll(removableNames)
        state = KeyBrowserState.Loaded(
            loaded.page.copy(keys = remainingKeys),
        )

        return TuiScreenResult.Continue
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        if (inputMode == KeyBrowserInputMode.PatternSearch) {
            return handlePatternInput(keyStroke)
        }
        if (inputMode == KeyBrowserInputMode.DeleteConfirmation) {
            return handleDeleteConfirmationInput(keyStroke)
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

            keyMatcher.isCharacter(keyStroke, 'd') -> {
                startDeleteConfirmation()
                TuiScreenResult.Continue
            }

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
        deleteJob?.cancel()
        loadingJob?.cancel()
        screenScope.cancel()
    }

    private fun handleDeleteConfirmationInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            keyMatcher.isEscape(keyStroke) || keyMatcher.isCharacter(keyStroke, 'n') -> {
                cancelDeleteConfirmation()
                TuiScreenResult.Continue
            }

            productionSafety && !productionDeleteAcknowledged && keyMatcher.isCharacter(keyStroke, 'p') -> {
                acknowledgeProductionDelete()
                TuiScreenResult.Continue
            }

            productionSafety && !productionDeleteAcknowledged && keyMatcher.isCharacter(keyStroke, 'y') -> {
                showProductionSafetyDeleteReminder()
                TuiScreenResult.Continue
            }

            keyMatcher.isCharacter(keyStroke, 'y') -> {
                submitDelete()
                TuiScreenResult.Continue
            }

            else -> TuiScreenResult.Continue
        }
    }

    private fun startDeleteConfirmation() {
        val key = selectedKey()
        if (readOnly) {
            key?.let {
                auditKeyDelete(
                    key = it,
                    result = OperationAuditResult.Blocked,
                    details = mapOf("reason" to "read-only"),
                )
            }
            showReadOnlyToast()
            return
        }
        if (isLoading() || deleteJob?.isActive == true) {
            return
        }

        if (key == null) return
        protectedNamespaceRules.firstMatch(key.name)?.let { match ->
            auditKeyDelete(
                key = key,
                result = OperationAuditResult.Blocked,
                details = mapOf("reason" to "protected-namespace", "rule" to match.rule),
            )
            showProtectedNamespaceToast(match.rule, match.keyName)
            return
        }
        pendingDeleteKey = key
        productionDeleteAcknowledged = false
        inputMode = KeyBrowserInputMode.DeleteConfirmation
        showDeleteConfirmationToast(key)
    }

    private fun showDeleteConfirmationToast(key: RedisKeySummary) {
        val productionLine = if (productionSafety && !productionDeleteAcknowledged) {
            "production safety: press p before y"
        } else {
            null
        }
        operationToast = KeyOperationToast.persistent(
            KeyOperationMessage.Failure("Delete \"${key.name}\"?"),
            "1 key · ${key.type.name.lowercase()} · ${formatTtl(key.ttl)} · ${formatMemory(key.memoryUsage)}",
            *listOfNotNull(productionLine).toTypedArray(),
            "y: confirm   n/ESC: cancel",
        )
    }

    private fun cancelDeleteConfirmation() {
        pendingDeleteKey = null
        productionDeleteAcknowledged = false
        inputMode = KeyBrowserInputMode.Browse
        operationToast = KeyOperationToast.transient(
            KeyOperationMessage.Info("Delete cancelled."),
        )
    }

    private fun acknowledgeProductionDelete() {
        val key = pendingDeleteKey ?: return
        productionDeleteAcknowledged = true
        showDeleteConfirmationToast(key)
    }

    private fun showProductionSafetyDeleteReminder() {
        val key = pendingDeleteKey ?: return
        operationToast = KeyOperationToast.persistent(
            KeyOperationMessage.Failure("Production safety enabled."),
            "Press p before confirming delete",
            "Key: ${key.name}",
        )
    }

    private fun submitDelete() {
        val key = pendingDeleteKey ?: return
        pendingDeleteKey = null
        productionDeleteAcknowledged = false
        inputMode = KeyBrowserInputMode.Browse
        auditKeyDelete(
            key = key,
            result = OperationAuditResult.Started,
        )
        operationToast = KeyOperationToast.persistent(
            KeyOperationMessage.Info("Deleting key..."),
        )

        deleteJob?.cancel()
        deleteJob = screenScope.launch {
            runCatching {
                deleteKeyUseCase.delete(
                    DeleteKeyRequest(
                        keyName = key.name,
                    )
                )
            }.onSuccess { result ->
                handleDeleteResult(key, result)
            }.onFailure { exception ->
                logger.warn("Failed to delete Redis key {} from browser.", key.name, exception)
                auditKeyDelete(
                    key = key,
                    result = OperationAuditResult.Failure,
                    details = mapOf("errorType" to exception::class.simpleName),
                )
                operationToast = KeyOperationToast.transient(
                    KeyOperationMessage.Failure("Delete failed."),
                    SafeOperationErrorMessage.from(exception),
                )
            }
        }
    }

    private fun handleDeleteResult(
        key: RedisKeySummary,
        result: DeleteKeyResult,
    ) {
        val refreshCursor = (state as? KeyBrowserState.Loaded)?.page?.cursor
            ?: KeyBrowserRenderer.INITIAL_CURSOR

        auditKeyDelete(
            key = key,
            result = when (result) {
                DeleteKeyResult.Deleted -> OperationAuditResult.Success
                DeleteKeyResult.KeyMissing -> OperationAuditResult.Missing
            },
        )

        operationToast = when (result) {
            DeleteKeyResult.Deleted -> KeyOperationToast.transient(
                KeyOperationMessage.Success("Key deleted."),
            )
            DeleteKeyResult.KeyMissing -> KeyOperationToast.transient(
                KeyOperationMessage.Failure("Key no longer exists."),
            )
        }
        loadPage(
            cursor = refreshCursor,
            clearOperationToast = false,
        )
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

        operationToast = null
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
        operationToast = null
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
                expireKeyUseCase = expireKeyUseCase,
                deleteKeyUseCase = deleteKeyUseCase,
                deleteKeyValueUseCase = deleteKeyValueUseCase,
                readOnly = readOnly,
                productionSafety = productionSafety,
                protectedNamespaceRules = protectedNamespaceRules,
                operationAuditLogger = operationAuditLogger,
                back = { recreateBrowser() },
            )
        )
    }

    private fun recreateBrowser(): TuiScreen {
        return KeyBrowserScreen(
            browseKeysUseCase = browseKeysUseCase,
            loadKeyDetailUseCase = loadKeyDetailUseCase,
            expireKeyUseCase = expireKeyUseCase,
            deleteKeyUseCase = deleteKeyUseCase,
            deleteKeyValueUseCase = deleteKeyValueUseCase,
            readOnly = readOnly,
            productionSafety = productionSafety,
            protectedNamespaceRules = protectedNamespaceRules,
            operationAuditLogger = operationAuditLogger,
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

        operationToast = null
        loadPage(cursor = loadedPage.nextCursor)
    }

    private fun loadPage(
        cursor: String,
        clearOperationToast: Boolean = true,
    ) {
        loadingJob?.cancel()
        pendingDeleteKey = null
        productionDeleteAcknowledged = false
        selectedKeyIndex = 0
        inputMode = KeyBrowserInputMode.Browse
        if (clearOperationToast) {
            operationToast = null
        }
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
                liveTtlTracker.observeAll(page.keys)
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
            RedisKeyTtlStatus.NoExpiration -> "no ttl"
            is RedisKeyTtlStatus.Expiring -> "${ttl.seconds}s ttl"
            is RedisKeyTtlStatus.Unknown -> "unknown ttl"
        }
    }

    private fun formatMemory(memoryUsage: RedisKeyMemoryUsage): String {
        return when (memoryUsage) {
            RedisKeyMemoryUsage.Unknown -> "unknown memory"
            is RedisKeyMemoryUsage.Known -> "${memoryUsage.bytes} B"
        }
    }

    private fun currentOperationToast(): KeyOperationToast? {
        if (inputMode == KeyBrowserInputMode.DeleteConfirmation) {
            return operationToast
        }

        val toast = operationToast ?: return null
        if (toast.isExpired()) {
            operationToast = null
            return null
        }
        return toast
    }

    private fun showReadOnlyToast() {
        operationToast = KeyOperationToast.transient(
            KeyOperationMessage.Failure("Read-only mode."),
            "Write operations are disabled",
        )
    }

    private fun showProtectedNamespaceToast(
        rule: String,
        keyName: String,
    ) {
        operationToast = KeyOperationToast.transient(
            KeyOperationMessage.Failure("Protected namespace."),
            "Rule: $rule",
            "Key: $keyName",
        )
    }

    private fun auditKeyDelete(
        key: RedisKeySummary,
        result: OperationAuditResult,
        details: Map<String, String?> = emptyMap(),
    ) {
        operationAuditLogger.record(
            action = "delete-key",
            keyName = key.name,
            target = "key",
            result = result,
            details = mapOf(
                "source" to "key-browser",
                "type" to key.type.name.lowercase(),
                "ttl" to formatTtl(key.ttl),
                "memory" to formatMemory(key.memoryUsage),
            ) + details,
        )
    }

    private companion object {
        const val EXPIRED_GRACE_MILLIS = 10_000L
    }
}
