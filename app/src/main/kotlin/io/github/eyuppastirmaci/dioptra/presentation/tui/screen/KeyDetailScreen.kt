package io.github.eyuppastirmaci.dioptra.presentation.tui.screen

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.TextColor
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyRequest
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyResult
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyUseCase
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyValueRequest
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyValueResult
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyValueTarget
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyValueUseCase
import io.github.eyuppastirmaci.dioptra.application.key.ExpireKeyRequest
import io.github.eyuppastirmaci.dioptra.application.key.ExpireKeyResult
import io.github.eyuppastirmaci.dioptra.application.key.ExpireKeyUseCase
import io.github.eyuppastirmaci.dioptra.application.key.LoadKeyDetailUseCase
import io.github.eyuppastirmaci.dioptra.application.safety.OperationAuditLogger
import io.github.eyuppastirmaci.dioptra.application.safety.OperationAuditResult
import io.github.eyuppastirmaci.dioptra.application.safety.ProtectedNamespaceRules
import io.github.eyuppastirmaci.dioptra.concurrency.DioptraCoroutineExceptionHandler
import io.github.eyuppastirmaci.dioptra.domain.key.RedisCollectionSizeSummary
import io.github.eyuppastirmaci.dioptra.domain.key.RedisHashFieldRow
import io.github.eyuppastirmaci.dioptra.domain.key.RedisListItemRow
import io.github.eyuppastirmaci.dioptra.domain.key.RedisSetMemberRow
import io.github.eyuppastirmaci.dioptra.domain.key.RedisSortedSetEntryRow
import io.github.eyuppastirmaci.dioptra.domain.key.RedisStreamEntryRow
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyDetailSnapshot
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyMemoryUsage
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeySummary
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyTtlStatus
import io.github.eyuppastirmaci.dioptra.domain.key.RedisKeyType
import io.github.eyuppastirmaci.dioptra.domain.key.RedisStringValuePreview
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.JsonPrettyFormatter
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.MetricRow
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.OperationToast
import io.github.eyuppastirmaci.dioptra.presentation.tui.component.Panel
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiContext
import io.github.eyuppastirmaci.dioptra.presentation.tui.core.TuiRect
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.SafeOperationErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keyoperation.KeyOperationMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keyoperation.KeyOperationToast
import io.github.eyuppastirmaci.dioptra.presentation.tui.ttl.LiveTtlTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class KeyDetailScreen(
    key: RedisKeySummary,
    private val loadKeyDetailUseCase: LoadKeyDetailUseCase,
    private val expireKeyUseCase: ExpireKeyUseCase,
    private val deleteKeyUseCase: DeleteKeyUseCase,
    private val deleteKeyValueUseCase: DeleteKeyValueUseCase,
    private val readOnly: Boolean,
    private val productionSafety: Boolean,
    private val protectedNamespaceRules: ProtectedNamespaceRules,
    private val operationAuditLogger: OperationAuditLogger,
    private val back: () -> TuiScreen,
) : TuiScreen {

    private val logger = LoggerFactory.getLogger(KeyDetailScreen::class.java)
    private val liveTtlTracker = LiveTtlTracker()
    private val screenScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            DioptraCoroutineExceptionHandler.create(
                logger = logger,
                contextName = "KeyDetailScreen",
                onError = { exception ->
                    state = KeyDetailState.Error(UserFacingErrorMessage.from(exception))
                },
            ),
    )
    @Volatile
    private var keySummary = key
    private var loadingJob: Job? = null
    private var expireJob: Job? = null
    private var deleteJob: Job? = null
    private var valueViewMode = ValueViewMode.Preview
    private var inputMode = KeyDetailInputMode.Browse
    private var expireInput = ""
    private var pendingDeleteTarget: DeleteTarget = DeleteTarget.None
    private var productionDeleteAcknowledged = false
    @Volatile
    private var operationToast: KeyOperationToast? = null
    private var cachedJsonSource: String? = null
    private var cachedJsonFormats: JsonPrettyFormatter.Result? = null

    @Volatile
    private var state: KeyDetailState = KeyDetailState.Loading

    private var liveCollection: LiveCollectionBuffer? = null
    private var collectionScrollOffset: Int = 0
    private var collectionSelectedIndex: Int = 0
    private var loadMoreInProgress: Boolean = false
    private var loadMoreJob: Job? = null

    init {
        liveTtlTracker.observe(key.name, key.ttl)
        loadDetail()
    }

    override fun render(context: TuiContext) {
        val ttlDisplay = liveTtlTracker.display(keySummary.name, keySummary.ttl)
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

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 1,
            text = "Dioptra Key Detail",
            foregroundColor = context.theme.title,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 3,
            text = keySummary.name.truncate(68),
            foregroundColor = if (ttlDisplay.expired) context.theme.danger else context.theme.value,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        MetricRow.draw(
            context = context,
            row = panelRect.top + 6,
            label = "Type",
            value = keySummary.type.name.lowercase(),
            labelColumn = panelRect.left + 3,
            valueColumn = panelRect.left + 28,
        )

        MetricRow.draw(
            context = context,
            row = panelRect.top + 7,
            label = "TTL",
            value = ttlDisplay.text,
            labelColumn = panelRect.left + 3,
            valueColumn = panelRect.left + 28,
            valueForegroundColor = ttlForegroundColor(context, ttlDisplay.expired),
        )

        MetricRow.draw(
            context = context,
            row = panelRect.top + 8,
            label = "Memory",
            value = formatMemoryUsage(keySummary.memoryUsage),
            labelColumn = panelRect.left + 3,
            valueColumn = panelRect.left + 28,
        )

        drawValuePreview(context, panelRect)

        val footerCore =
            if (readOnly) {
                "read-only   v: ${valueViewMode.nextActionLabel}   b/ESC: key browser   q: exit"
            } else if (productionSafety) {
                "prod-safety   d: delete   e: expire   v: ${valueViewMode.nextActionLabel}   b/ESC: key browser"
            } else {
                "d: delete   e: expire   v: ${valueViewMode.nextActionLabel}   b/ESC: key browser   q: exit"
            }
        val footerText =
            if (liveCollection != null) {
                "↑↓: select · Enter: next page · $footerCore"
            } else {
                footerCore
            }
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + panelRect.height - 2,
            text = footerText.truncate(VALUE_LINE_WIDTH),
            foregroundColor = context.theme.hint,
            backgroundColor = context.theme.panel,
        )

        OperationToast.draw(
            context = context,
            containerRect = panelRect,
            toast = currentOperationToast(),
        )
    }

    override fun tick(): TuiScreenResult {
        if (liveTtlTracker.expiredGraceComplete(keySummary.name, keySummary.ttl, EXPIRED_GRACE_MILLIS)) {
            keySummary = keySummary.copy(ttl = RedisKeyTtlStatus.KeyDoesNotExist)
            liveTtlTracker.forget(keySummary.name)
            liveCollection = null
            refreshLoadedSnapshotKey()
        }

        return TuiScreenResult.Continue
    }

    override fun handleInput(keyStroke: KeyStroke): TuiScreenResult {
        if (inputMode == KeyDetailInputMode.ExpireSeconds) {
            return handleExpireInput(keyStroke)
        }
        if (inputMode == KeyDetailInputMode.DeleteConfirmation) {
            return handleDeleteConfirmationInput(keyStroke)
        }

        return when {
            isCharacter(keyStroke, 'd') -> {
                startDeleteConfirmation()
                TuiScreenResult.Continue
            }

            isCharacter(keyStroke, 'e') -> {
                startExpireInput()
                TuiScreenResult.Continue
            }

            isCharacter(keyStroke, 'v') -> {
                toggleValueViewMode()
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.ArrowDown -> {
                if (adjustCollectionSelection(1)) {
                    TuiScreenResult.Continue
                } else {
                    TuiScreenResult.Continue
                }
            }

            keyStroke.keyType == KeyType.ArrowUp -> {
                if (adjustCollectionSelection(-1)) {
                    TuiScreenResult.Continue
                } else {
                    TuiScreenResult.Continue
                }
            }

            keyStroke.keyType == KeyType.Enter -> {
                if (handleCollectionEnter()) {
                    TuiScreenResult.Continue
                } else {
                    TuiScreenResult.Continue
                }
            }

            isBackKey(keyStroke) -> TuiScreenResult.Navigate(back.invoke())
            isExitKey(keyStroke) -> TuiScreenResult.Exit
            else -> TuiScreenResult.Continue
        }
    }

    private fun handleDeleteConfirmationInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            keyStroke.keyType == KeyType.Escape || isCharacter(keyStroke, 'n') -> {
                cancelDeleteConfirmation()
                TuiScreenResult.Continue
            }

            productionSafety && !productionDeleteAcknowledged && isCharacter(keyStroke, 'p') -> {
                productionDeleteAcknowledged = true
                operationToast = null
                TuiScreenResult.Continue
            }

            productionSafety && !productionDeleteAcknowledged && isCharacter(keyStroke, 'y') -> {
                operationToast = KeyOperationToast.persistent(
                    KeyOperationMessage.Failure("Production safety enabled."),
                    "Press p before confirming delete",
                    "Key: ${keySummary.name}",
                )
                TuiScreenResult.Continue
            }

            isCharacter(keyStroke, 'y') -> {
                submitDelete()
                TuiScreenResult.Continue
            }

            else -> TuiScreenResult.Continue
        }
    }

    private fun startDeleteConfirmation() {
        if (readOnly) {
            auditDeleteSelection(
                target = DeleteTarget.None,
                result = OperationAuditResult.Blocked,
                details = mapOf("reason" to "read-only"),
            )
            showReadOnlyToast()
            return
        }
        if (deleteJob?.isActive == true || keySummary.ttl == RedisKeyTtlStatus.KeyDoesNotExist) {
            return
        }
        protectedNamespaceRules.firstMatch(keySummary.name)?.let { match ->
            auditDeleteSelection(
                target = DeleteTarget.None,
                result = OperationAuditResult.Blocked,
                details = mapOf("reason" to "protected-namespace", "rule" to match.rule),
            )
            showProtectedNamespaceToast(match.rule, match.keyName)
            return
        }

        val target = deleteTargetForCurrentSelection()
        if (target == DeleteTarget.None) {
            operationToast = KeyOperationToast.transient(
                KeyOperationMessage.Info("Select a value first."),
                "Use Up/Down on a collection row",
            )
            return
        }

        pendingDeleteTarget = target
        productionDeleteAcknowledged = false
        expireInput = ""
        inputMode = KeyDetailInputMode.DeleteConfirmation
        operationToast = null
    }

    private fun cancelDeleteConfirmation() {
        pendingDeleteTarget = DeleteTarget.None
        productionDeleteAcknowledged = false
        inputMode = KeyDetailInputMode.Browse
        operationToast = KeyOperationToast.transient(
            KeyOperationMessage.Info("Delete cancelled."),
        )
    }

    private fun submitDelete() {
        val target = pendingDeleteTarget
        if (target == DeleteTarget.None) {
            inputMode = KeyDetailInputMode.Browse
            operationToast = KeyOperationToast.transient(
                KeyOperationMessage.Info("No value selected."),
            )
            return
        }

        val keyName = keySummary.name
        pendingDeleteTarget = DeleteTarget.None
        productionDeleteAcknowledged = false
        inputMode = KeyDetailInputMode.Browse
        auditDeleteSelection(
            target = target,
            result = OperationAuditResult.Started,
        )
        operationToast = KeyOperationToast.persistent(target.inProgressToast())

        deleteJob?.cancel()
        deleteJob = screenScope.launch {
            runCatching {
                deleteTarget(keyName, target)
            }.onSuccess { result ->
                handleDeleteTargetResult(keyName, target, result)
            }.onFailure { exception ->
                logger.warn("Failed to delete Redis key {}.", keyName, exception)
                auditDeleteSelection(
                    keyName = keyName,
                    target = target,
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

    private suspend fun deleteTarget(
        keyName: String,
        target: DeleteTarget,
    ): DeleteTargetResult {
        return when (target) {
            DeleteTarget.None -> DeleteTargetResult.Missing
            is DeleteTarget.Key -> when (deleteKeyUseCase.delete(DeleteKeyRequest(keyName))) {
                DeleteKeyResult.Deleted -> DeleteTargetResult.Deleted
                DeleteKeyResult.KeyMissing -> DeleteTargetResult.Missing
            }

            is DeleteTarget.Value -> when (
                deleteKeyValueUseCase.delete(
                    DeleteKeyValueRequest(
                        keyName = keyName,
                        target = target.target,
                    )
                )
            ) {
                DeleteKeyValueResult.Deleted -> DeleteTargetResult.Deleted
                DeleteKeyValueResult.Missing -> DeleteTargetResult.Missing
            }
        }
    }

    private fun handleDeleteTargetResult(
        keyName: String,
        target: DeleteTarget,
        result: DeleteTargetResult,
    ) {
        auditDeleteSelection(
            keyName = keyName,
            target = target,
            result = when (result) {
                DeleteTargetResult.Deleted -> OperationAuditResult.Success
                DeleteTargetResult.Missing -> OperationAuditResult.Missing
            },
        )

        operationToast = when (result) {
            DeleteTargetResult.Deleted -> KeyOperationToast.transient(target.successToast())
            DeleteTargetResult.Missing -> KeyOperationToast.transient(target.missingToast())
        }

        when (target) {
            is DeleteTarget.Key -> {
                keySummary = keySummary.copy(
                    ttl = RedisKeyTtlStatus.KeyDoesNotExist,
                )
                liveTtlTracker.forget(keySummary.name)
                liveCollection = null
                refreshLoadedSnapshotKey()
            }

            is DeleteTarget.Value -> loadDetail()
            DeleteTarget.None -> Unit
        }
    }

    private fun deleteTargetForCurrentSelection(): DeleteTarget {
        if (keySummary.type == RedisKeyType.STRING) {
            return DeleteTarget.Key(
                previewLines = destructiveKeyPreview(),
            )
        }

        val buf = liveCollection ?: return DeleteTarget.None
        if (collectionSelectedIndex !in 0 until buf.rowCount()) {
            return DeleteTarget.None
        }

        return when (buf) {
            is LiveCollectionBuffer.HashBuf ->
                buf.rows.getOrNull(collectionSelectedIndex)?.let { row ->
                    DeleteTarget.Value(
                        target = DeleteKeyValueTarget.HashField(row.field),
                        subject = "hash field",
                        previewLines = listOf(
                            "field: ${row.field.singleLinePreview()}",
                            "key: ${keySummary.name}",
                        ),
                    )
                } ?: DeleteTarget.None

            is LiveCollectionBuffer.ListBuf ->
                buf.rows.getOrNull(collectionSelectedIndex)?.let { row ->
                    DeleteTarget.Value(
                        target = DeleteKeyValueTarget.ListItem(row.index),
                        subject = "list item",
                        previewLines = listOf(
                            "index: ${row.index}",
                            "value: ${formatInlineValuePreview(row.valuePreview)}",
                        ),
                    )
                } ?: DeleteTarget.None

            is LiveCollectionBuffer.SetBuf ->
                buf.rows.getOrNull(collectionSelectedIndex)?.let { row ->
                    DeleteTarget.Value(
                        target = DeleteKeyValueTarget.SetMember(row.rawValue),
                        subject = "set member",
                        previewLines = listOf(
                            "member: ${formatInlineValuePreview(row.valuePreview)}",
                            "key: ${keySummary.name}",
                        ),
                    )
                } ?: DeleteTarget.None

            is LiveCollectionBuffer.ZSetBuf ->
                buf.rows.getOrNull(collectionSelectedIndex)?.let { row ->
                    DeleteTarget.Value(
                        target = DeleteKeyValueTarget.SortedSetMember(row.rawMember),
                        subject = "zset member",
                        previewLines = listOf(
                            "score: ${formatSortedSetScore(row.score)}",
                            "member: ${formatInlineValuePreview(row.memberPreview)}",
                        ),
                    )
                } ?: DeleteTarget.None

            is LiveCollectionBuffer.StreamBuf ->
                buf.rows.getOrNull(collectionSelectedIndex)?.let { row ->
                    DeleteTarget.Value(
                        target = DeleteKeyValueTarget.StreamEntry(row.entryId),
                        subject = "stream entry",
                        previewLines = listOf(
                            "entry id: ${row.entryId}",
                            "key: ${keySummary.name}",
                        ),
                    )
                } ?: DeleteTarget.None
        }
    }

    private fun destructiveKeyPreview(): List<String> {
        val base = "1 key · ${keySummary.type.name.lowercase()} · ${formatTtl(keySummary.ttl)}"
        val memory = "memory: ${formatMemoryUsage(keySummary.memoryUsage)}"
        val collection = (state as? KeyDetailState.Loaded)
            ?.snapshot
            ?.collectionSize
            ?.let(::formatCollectionPreview)

        return listOfNotNull(
            base,
            collection,
            memory,
        )
    }

    private fun formatCollectionPreview(summary: RedisCollectionSizeSummary): String {
        return when (summary) {
            is RedisCollectionSizeSummary.Known ->
                "${summary.memberCount} ${summary.kind.nounShort} affected"
            RedisCollectionSizeSummary.Unavailable -> "collection size unknown"
        }
    }

    private fun handleExpireInput(keyStroke: KeyStroke): TuiScreenResult {
        return when {
            keyStroke.keyType == KeyType.Escape -> {
                cancelExpireInput()
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.Enter -> {
                submitExpireInput()
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.Backspace -> {
                expireInput = expireInput.dropLast(1)
                operationToast = null
                TuiScreenResult.Continue
            }

            keyStroke.keyType == KeyType.Character -> {
                appendExpireInput(keyStroke.character)
                TuiScreenResult.Continue
            }

            else -> TuiScreenResult.Continue
        }
    }

    private fun startExpireInput() {
        if (readOnly) {
            auditExpire(
                result = OperationAuditResult.Blocked,
                details = mapOf("reason" to "read-only"),
            )
            showReadOnlyToast()
            return
        }
        if (expireJob?.isActive == true) {
            return
        }
        protectedNamespaceRules.firstMatch(keySummary.name)?.let { match ->
            auditExpire(
                result = OperationAuditResult.Blocked,
                details = mapOf("reason" to "protected-namespace", "rule" to match.rule),
            )
            showProtectedNamespaceToast(match.rule, match.keyName)
            return
        }

        expireInput = ""
        inputMode = KeyDetailInputMode.ExpireSeconds
        operationToast = null
    }

    private fun cancelExpireInput() {
        expireInput = ""
        inputMode = KeyDetailInputMode.Browse
        operationToast = KeyOperationToast.transient(
            KeyOperationMessage.Info("Expire cancelled."),
        )
    }

    private fun appendExpireInput(character: Char?) {
        if (character == null || !character.isDigit()) {
            return
        }
        if (expireInput.length >= MAX_EXPIRE_SECONDS_DIGITS) {
            return
        }

        expireInput += character
        operationToast = null
    }

    private fun submitExpireInput() {
        val seconds = expireInput.toLongOrNull()
        if (seconds == null || seconds <= 0) {
            operationToast = KeyOperationToast.persistent(
                KeyOperationMessage.Failure("TTL must be positive."),
                "Enter seconds greater than 0",
            )
            return
        }

        val keyName = keySummary.name
        expireInput = ""
        inputMode = KeyDetailInputMode.Browse
        auditExpire(
            keyName = keyName,
            result = OperationAuditResult.Started,
            details = mapOf("seconds" to seconds.toString()),
        )
        operationToast = KeyOperationToast.persistent(
            KeyOperationMessage.Info("Applying TTL..."),
        )

        expireJob?.cancel()
        expireJob = screenScope.launch {
            runCatching {
                expireKeyUseCase.expire(
                    ExpireKeyRequest(
                        keyName = keyName,
                        seconds = seconds,
                    )
                )
            }.onSuccess { result ->
                handleExpireResult(keyName, result)
            }.onFailure { exception ->
                logger.warn("Failed to expire Redis key {}.", keyName, exception)
                auditExpire(
                    keyName = keyName,
                    result = OperationAuditResult.Failure,
                    details = mapOf(
                        "seconds" to seconds.toString(),
                        "errorType" to exception::class.simpleName,
                    ),
                )
                operationToast = KeyOperationToast.transient(
                    KeyOperationMessage.Failure("Expire failed."),
                    SafeOperationErrorMessage.from(exception),
                )
            }
        }
    }

    private fun handleExpireResult(
        keyName: String,
        result: ExpireKeyResult,
    ) {
        auditExpire(
            keyName = keyName,
            result = when (result) {
                is ExpireKeyResult.Updated -> OperationAuditResult.Success
                ExpireKeyResult.KeyMissing -> OperationAuditResult.Missing
            },
            details = mapOf(
                "seconds" to (result as? ExpireKeyResult.Updated)?.seconds?.toString(),
            ),
        )

        when (result) {
            is ExpireKeyResult.Updated -> {
                keySummary = keySummary.copy(
                    ttl = RedisKeyTtlStatus.Expiring(result.seconds),
                )
                liveTtlTracker.observe(keySummary.name, keySummary.ttl)
                refreshLoadedSnapshotKey()
                operationToast = KeyOperationToast.transient(
                    KeyOperationMessage.Success("TTL set to ${result.seconds}s."),
                )
            }

            ExpireKeyResult.KeyMissing -> {
                keySummary = keySummary.copy(
                    ttl = RedisKeyTtlStatus.KeyDoesNotExist,
                )
                liveTtlTracker.forget(keySummary.name)
                refreshLoadedSnapshotKey()
                operationToast = KeyOperationToast.transient(
                    KeyOperationMessage.Failure("Key no longer exists."),
                )
            }
        }
    }

    private fun currentOperationToast(): KeyOperationToast? {
        return when (inputMode) {
            KeyDetailInputMode.Browse -> activeStoredOperationToast()
            KeyDetailInputMode.ExpireSeconds ->
                activeStoredOperationToast()
                    ?: KeyOperationToast.persistent(
                        KeyOperationMessage.Info("Expire after seconds: ${expireInput.ifEmpty { "_" }}"),
                        *productionSafetyDetails().toTypedArray(),
                        "Enter: apply   ESC: cancel",
                    )
            KeyDetailInputMode.DeleteConfirmation -> KeyOperationToast.persistent(
                pendingDeleteTarget.confirmationToast(),
                *pendingDeleteTarget.previewLines().toTypedArray(),
                *productionDeleteConfirmationDetails().toTypedArray(),
                productionDeleteActionHint(),
            )
        }
    }

    private fun activeStoredOperationToast(): KeyOperationToast? {
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

    private fun auditDeleteSelection(
        target: DeleteTarget,
        result: OperationAuditResult,
        details: Map<String, String?> = emptyMap(),
        keyName: String = keySummary.name,
    ) {
        operationAuditLogger.record(
            action = target.auditAction(),
            keyName = keyName,
            target = target.auditTarget(),
            result = result,
            details = mapOf(
                "source" to "key-detail",
                "type" to keySummary.type.name.lowercase(),
                "ttl" to formatTtl(keySummary.ttl),
                "memory" to formatMemoryUsage(keySummary.memoryUsage),
            ) + details,
        )
    }

    private fun auditExpire(
        result: OperationAuditResult,
        details: Map<String, String?> = emptyMap(),
        keyName: String = keySummary.name,
    ) {
        operationAuditLogger.record(
            action = "expire-key",
            keyName = keyName,
            target = "key",
            result = result,
            details = mapOf(
                "source" to "key-detail",
                "type" to keySummary.type.name.lowercase(),
                "ttl" to formatTtl(keySummary.ttl),
                "memory" to formatMemoryUsage(keySummary.memoryUsage),
            ) + details,
        )
    }

    private fun productionSafetyDetails(): List<String> {
        return if (productionSafety) {
            listOf("production safety enabled")
        } else {
            emptyList()
        }
    }

    private fun productionDeleteConfirmationDetails(): List<String> {
        return if (productionSafety && !productionDeleteAcknowledged) {
            listOf("production safety: press p before y")
        } else {
            emptyList()
        }
    }

    private fun productionDeleteActionHint(): String {
        return if (productionSafety && !productionDeleteAcknowledged) {
            "p: acknowledge   n/ESC: cancel"
        } else {
            "y: confirm   n/ESC: cancel"
        }
    }

    private fun refreshLoadedSnapshotKey() {
        val currentState = state as? KeyDetailState.Loaded ?: return
        state = currentState.copy(
            snapshot = currentState.snapshot.copy(
                key = keySummary,
            ),
        )
    }

    override fun close() {
        deleteJob?.cancel()
        expireJob?.cancel()
        loadMoreJob?.cancel()
        loadingJob?.cancel()
        screenScope.cancel()
    }

    private fun loadDetail() {
        loadingJob?.cancel()
        loadMoreJob?.cancel()
        liveCollection = null
        collectionScrollOffset = 0
        collectionSelectedIndex = 0
        loadMoreInProgress = false
        cachedJsonSource = null
        cachedJsonFormats = null
        state = KeyDetailState.Loading

        loadingJob = screenScope.launch {
            runCatching {
                loadKeyDetailUseCase.load(keySummary)
            }.onSuccess { snapshot ->
                keySummary = snapshot.key
                liveTtlTracker.observe(keySummary.name, keySummary.ttl)
                liveCollection = LiveCollectionBuffer.fromSnapshot(snapshot)
                collectionScrollOffset = 0
                collectionSelectedIndex = 0
                state = KeyDetailState.Loaded(snapshot)
            }.onFailure { exception ->
                logger.error("Failed to load Redis key detail for {}.", keySummary.name, exception)
                state = KeyDetailState.Error(UserFacingErrorMessage.from(exception))
            }
        }
    }

    private fun collectionValueStartRow(
        snapshot: RedisKeyDetailSnapshot,
        panelRect: TuiRect,
    ): Int =
        when (snapshot.collectionSize) {
            RedisCollectionSizeSummary.Unavailable -> panelRect.top + 13
            else -> panelRect.top + 12
        }

    private data class CollectionViewportLayout(
        val maxDataRows: Int,
        val hintRow: Int,
        val loadMoreRow: Int,
    )

    private fun collectionViewportLayout(
        panelRect: TuiRect,
        startRow: Int,
    ): CollectionViewportLayout {
        val footerRow = panelRect.top + panelRect.height - 2
        val maxDataRows = (footerRow - startRow - 2).coerceAtLeast(1)
        return CollectionViewportLayout(
            maxDataRows = maxDataRows,
            hintRow = startRow + maxDataRows,
            loadMoreRow = startRow + maxDataRows + 1,
        )
    }

    private fun clearPanelLine(
        context: TuiContext,
        panelRect: TuiRect,
        row: Int,
    ) {
        context.fillRect(
            rect = TuiRect(
                left = panelRect.left + 1,
                top = row,
                width = panelRect.width - 2,
                height = 1,
            ),
            backgroundColor = context.theme.panel,
        )
    }

    private fun collectionSelectableLastIndex(): Int {
        val buf = liveCollection ?: return -1
        val extra = if (buf.canLoadMore()) 1 else 0
        return buf.rowCount() + extra - 1
    }

    private fun adjustCollectionSelection(delta: Int): Boolean {
        if (state !is KeyDetailState.Loaded || keySummary.type == RedisKeyType.STRING) {
            return false
        }
        if (liveCollection == null) {
            return false
        }
        val last = collectionSelectableLastIndex()
        if (last < 0) {
            return false
        }
        collectionSelectedIndex = (collectionSelectedIndex + delta).coerceIn(0, last)
        ensureCollectionScrollVisible()
        return true
    }

    private fun handleCollectionEnter(): Boolean {
        if (state !is KeyDetailState.Loaded || keySummary.type == RedisKeyType.STRING) {
            return false
        }
        val buf = liveCollection ?: return false
        if (!buf.canLoadMore() || loadMoreInProgress) {
            return false
        }
        requestLoadNextPage()
        return true
    }

    private fun ensureCollectionScrollVisible() {
        val buf = liveCollection ?: return
        val snapshot = (state as? KeyDetailState.Loaded)?.snapshot ?: return
        val panelRect = TuiRect(left = 2, top = 1, width = 84, height = 21)
        val startRow = collectionValueStartRow(snapshot, panelRect)
        val layout = collectionViewportLayout(panelRect, startRow)
        val rowsCount = buf.rowCount()
        val maxRows = layout.maxDataRows
        if (collectionSelectedIndex < rowsCount) {
            if (collectionSelectedIndex < collectionScrollOffset) {
                collectionScrollOffset = collectionSelectedIndex
            }
            if (collectionSelectedIndex >= collectionScrollOffset + maxRows) {
                collectionScrollOffset = collectionSelectedIndex - maxRows + 1
            }
        } else if (buf.canLoadMore() && collectionSelectedIndex == rowsCount) {
            collectionScrollOffset = (rowsCount - maxRows).coerceAtLeast(0)
        }
    }

    private fun requestLoadNextPage() {
        val buf = liveCollection ?: return
        loadMoreJob?.cancel()
        loadMoreInProgress = true
        loadMoreJob = screenScope.launch {
            try {
                runCatching {
                    loadOnePage(buf)
                }.onFailure { exception ->
                    logger.error("Load more collection page failed for {}.", keySummary.name, exception)
                }
                val last = collectionSelectableLastIndex()
                if (last >= 0) {
                    collectionSelectedIndex = collectionSelectedIndex.coerceIn(0, last)
                }
                ensureCollectionScrollVisible()
            } finally {
                loadMoreInProgress = false
            }
        }
    }

    private suspend fun loadOnePage(buf: LiveCollectionBuffer) {
        when (buf) {
            is LiveCollectionBuffer.HashBuf -> loadHashChunk(buf)
            is LiveCollectionBuffer.ListBuf -> {
                val start = buf.nextStart ?: return
                val pair = loadKeyDetailUseCase.loadMoreListItems(keySummary.name, start)
                if (pair == null) {
                    buf.nextStart = null
                    return
                }
                buf.rows.addAll(pair.first)
                buf.nextStart = pair.second
            }

            is LiveCollectionBuffer.SetBuf -> loadSetChunk(buf)
            is LiveCollectionBuffer.ZSetBuf -> loadZSetChunk(buf)

            is LiveCollectionBuffer.StreamBuf -> {
                val id = buf.afterId ?: return
                val pair = loadKeyDetailUseCase.loadMoreStreamEntries(keySummary.name, id)
                if (pair == null) {
                    buf.afterId = null
                    return
                }
                buf.rows.addAll(pair.first)
                buf.afterId = pair.second
            }
        }
    }

    private suspend fun loadHashChunk(buf: LiveCollectionBuffer.HashBuf) {
        val target = buf.rows.size + HASH_PAGE_SIZE
        while (buf.rows.size < target && buf.overflow.isNotEmpty()) {
            buf.rows.add(buf.overflow.removeFirst())
        }
        while (buf.rows.size < target && buf.cursor != null) {
            val cursorVal = buf.cursor!!
            val result = loadKeyDetailUseCase.loadMoreHashFields(keySummary.name, cursorVal) ?: run {
                buf.cursor = null
                return
            }
            val needed = target - buf.rows.size
            if (result.newRows.size <= needed) {
                buf.rows.addAll(result.newRows)
            } else {
                buf.rows.addAll(result.newRows.subList(0, needed))
                for (i in needed until result.newRows.size) {
                    buf.overflow.addLast(result.newRows[i])
                }
            }
            result.overflow.forEach { buf.overflow.addLast(it) }
            buf.cursor = result.nextCursor
        }
    }

    private suspend fun loadSetChunk(buf: LiveCollectionBuffer.SetBuf) {
        val target = buf.rows.size + SET_PAGE_SIZE
        while (buf.rows.size < target && buf.overflow.isNotEmpty()) {
            buf.rows.add(buf.overflow.removeFirst())
        }
        while (buf.rows.size < target && buf.cursor != null) {
            val cursorVal = buf.cursor!!
            val result = loadKeyDetailUseCase.loadMoreSetMembers(keySummary.name, cursorVal) ?: run {
                buf.cursor = null
                return
            }
            val needed = target - buf.rows.size
            if (result.newRows.size <= needed) {
                buf.rows.addAll(result.newRows)
            } else {
                buf.rows.addAll(result.newRows.subList(0, needed))
                for (i in needed until result.newRows.size) {
                    buf.overflow.addLast(result.newRows[i])
                }
            }
            result.overflow.forEach { buf.overflow.addLast(it) }
            buf.cursor = result.nextCursor
        }
    }

    private suspend fun loadZSetChunk(buf: LiveCollectionBuffer.ZSetBuf) {
        val target = buf.rows.size + ZSET_PAGE_SIZE
        while (buf.rows.size < target && buf.overflow.isNotEmpty()) {
            buf.rows.add(buf.overflow.removeFirst())
        }
        while (buf.rows.size < target && buf.cursor != null) {
            val cursorVal = buf.cursor!!
            val result = loadKeyDetailUseCase.loadMoreSortedSetEntries(keySummary.name, cursorVal) ?: run {
                buf.cursor = null
                return
            }
            val needed = target - buf.rows.size
            if (result.newRows.size <= needed) {
                buf.rows.addAll(result.newRows)
            } else {
                buf.rows.addAll(result.newRows.subList(0, needed))
                for (i in needed until result.newRows.size) {
                    buf.overflow.addLast(result.newRows[i])
                }
            }
            result.overflow.forEach { buf.overflow.addLast(it) }
            buf.cursor = result.nextCursor
        }
    }

    private fun collectionStillBehindRedis(
        summary: RedisCollectionSizeSummary.Known?,
        loadedRows: Int,
        buf: LiveCollectionBuffer,
    ): Boolean {
        if (buf.canLoadMore()) {
            return true
        }
        return summary != null && loadedRows < summary.memberCount
    }

    private sealed interface LiveCollectionBuffer {
        fun rowCount(): Int
        fun canLoadMore(): Boolean

        data class HashBuf(
            val rows: MutableList<RedisHashFieldRow>,
            val overflow: ArrayDeque<RedisHashFieldRow>,
            var cursor: String?,
        ) : LiveCollectionBuffer {
            override fun rowCount(): Int = rows.size
            override fun canLoadMore(): Boolean = cursor != null || overflow.isNotEmpty()
        }

        data class ListBuf(
            val rows: MutableList<RedisListItemRow>,
            var nextStart: Long?,
        ) : LiveCollectionBuffer {
            override fun rowCount(): Int = rows.size
            override fun canLoadMore(): Boolean = nextStart != null
        }

        data class SetBuf(
            val rows: MutableList<RedisSetMemberRow>,
            val overflow: ArrayDeque<RedisSetMemberRow>,
            var cursor: String?,
        ) : LiveCollectionBuffer {
            override fun rowCount(): Int = rows.size
            override fun canLoadMore(): Boolean = cursor != null || overflow.isNotEmpty()
        }

        data class ZSetBuf(
            val rows: MutableList<RedisSortedSetEntryRow>,
            val overflow: ArrayDeque<RedisSortedSetEntryRow>,
            var cursor: String?,
        ) : LiveCollectionBuffer {
            override fun rowCount(): Int = rows.size
            override fun canLoadMore(): Boolean = cursor != null || overflow.isNotEmpty()
        }

        data class StreamBuf(
            val rows: MutableList<RedisStreamEntryRow>,
            var afterId: String?,
        ) : LiveCollectionBuffer {
            override fun rowCount(): Int = rows.size
            override fun canLoadMore(): Boolean = afterId != null
        }

        companion object {
            fun fromSnapshot(snapshot: RedisKeyDetailSnapshot): LiveCollectionBuffer? {
                return when (snapshot.key.type) {
                    RedisKeyType.HASH ->
                        snapshot.hashFieldsPreview?.let {
                            HashBuf(
                                rows = it.rows.toMutableList(),
                                overflow = ArrayDeque(it.overflow),
                                cursor = it.loadMoreCursor,
                            )
                        }

                    RedisKeyType.LIST ->
                        snapshot.listItemsPreview?.let {
                            ListBuf(it.rows.toMutableList(), it.nextStartIndex)
                        }

                    RedisKeyType.SET ->
                        snapshot.setMembersPreview?.let {
                            SetBuf(
                                rows = it.rows.toMutableList(),
                                overflow = ArrayDeque(it.overflow),
                                cursor = it.loadMoreCursor,
                            )
                        }

                    RedisKeyType.ZSET ->
                        snapshot.sortedSetEntriesPreview?.let {
                            ZSetBuf(
                                rows = it.rows.toMutableList(),
                                overflow = ArrayDeque(it.overflow),
                                cursor = it.loadMoreCursor,
                            )
                        }

                    RedisKeyType.STREAM ->
                        snapshot.streamEntriesPreview?.let {
                            StreamBuf(it.rows.toMutableList(), it.loadMoreAfterId)
                        }

                    else -> null
                }
            }
        }
    }

    private fun drawValuePreview(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        if (keySummary.ttl == RedisKeyTtlStatus.KeyDoesNotExist) {
            context.putText(
                column = panelRect.left + 3,
                row = panelRect.top + 11,
                text = "Key is missing or was deleted.",
                foregroundColor = context.theme.warning,
                backgroundColor = context.theme.panel,
                bold = true,
            )
            return
        }

        when (val currentState = state) {
            KeyDetailState.Loading -> {
                context.putText(
                    column = panelRect.left + 3,
                    row = panelRect.top + 11,
                    text = "Loading value preview...",
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
            }

            is KeyDetailState.Loaded -> drawLoadedValuePreview(context, panelRect, currentState.snapshot)

            is KeyDetailState.Error -> {
                context.putText(
                    column = panelRect.left + 3,
                    row = panelRect.top + 11,
                    text = "Could not load value preview: ${currentState.message}".truncate(68),
                    foregroundColor = context.theme.warning,
                    backgroundColor = context.theme.panel,
                )
            }
        }
    }

    private fun drawNonStringCollection(
        context: TuiContext,
        panelRect: TuiRect,
        snapshot: RedisKeyDetailSnapshot,
    ) {
        when (val summary = snapshot.collectionSize) {
            null -> {
                context.putText(
                    column = panelRect.left + 3,
                    row = panelRect.top + 11,
                    text = "Collection size is not available for this key type.".truncate(VALUE_LINE_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )

                when (snapshot.key.type) {
                    RedisKeyType.HASH -> drawHashFieldRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = null,
                    )

                    RedisKeyType.LIST -> drawListItemRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = null,
                    )

                    RedisKeyType.SET -> drawSetMemberRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = null,
                    )

                    RedisKeyType.ZSET -> drawSortedSetEntryRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = null,
                    )

                    RedisKeyType.STREAM -> drawStreamEntryRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = null,
                    )

                    else -> Unit
                }
            }

            is RedisCollectionSizeSummary.Known -> {
                MetricRow.draw(
                    context = context,
                    row = panelRect.top + 11,
                    label = summary.kind.metricLabel,
                    value = summary.memberCount.toString(),
                    labelColumn = panelRect.left + 3,
                    valueColumn = panelRect.left + 28,
                )

                when (snapshot.key.type) {
                    RedisKeyType.HASH -> drawHashFieldRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = summary,
                    )

                    RedisKeyType.LIST -> drawListItemRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = summary,
                    )

                    RedisKeyType.SET -> drawSetMemberRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = summary,
                    )

                    RedisKeyType.ZSET -> drawSortedSetEntryRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = summary,
                    )

                    RedisKeyType.STREAM -> drawStreamEntryRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = summary,
                    )

                    else -> {
                        context.putText(
                            column = panelRect.left + 3,
                            row = panelRect.top + 12,
                            text = "Listing ${summary.kind.nounShort} arrives in the next detail steps.".truncate(VALUE_LINE_WIDTH),
                            foregroundColor = context.theme.hint,
                            backgroundColor = context.theme.panel,
                        )
                    }
                }
            }

            RedisCollectionSizeSummary.Unavailable -> {
                context.putText(
                    column = panelRect.left + 3,
                    row = panelRect.top + 11,
                    text = "Could not read collection size (wrong type or Redis error).".truncate(VALUE_LINE_WIDTH),
                    foregroundColor = context.theme.warning,
                    backgroundColor = context.theme.panel,
                )

                context.putText(
                    column = panelRect.left + 3,
                    row = panelRect.top + 12,
                    text = "Verify the key still matches the reported type.".truncate(VALUE_LINE_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )

                when (snapshot.key.type) {
                    RedisKeyType.HASH -> drawHashFieldRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = null,
                    )

                    RedisKeyType.LIST -> drawListItemRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = null,
                    )

                    RedisKeyType.SET -> drawSetMemberRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = null,
                    )

                    RedisKeyType.ZSET -> drawSortedSetEntryRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = null,
                    )

                    RedisKeyType.STREAM -> drawStreamEntryRows(
                        context = context,
                        panelRect = panelRect,
                        snapshot = snapshot,
                        summary = null,
                    )

                    else -> Unit
                }
            }
        }
    }

    private fun drawStreamEntryRows(
        context: TuiContext,
        panelRect: TuiRect,
        snapshot: RedisKeyDetailSnapshot,
        summary: RedisCollectionSizeSummary.Known?,
    ) {
        val preview = snapshot.streamEntriesPreview
        val startRow = collectionValueStartRow(snapshot, panelRect)
        val layout = collectionViewportLayout(panelRect, startRow)
        val buf = liveCollection as? LiveCollectionBuffer.StreamBuf
        val rows = buf?.rows ?: preview?.rows ?: emptyList()

        if (preview == null) {
            context.putText(
                column = panelRect.left + 3,
                row = startRow,
                text = "Could not load stream entries.".truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.warning,
                backgroundColor = context.theme.panel,
            )
            return
        }

        if (rows.isEmpty()) {
            context.putText(
                column = panelRect.left + 3,
                row = startRow,
                text = "Stream has no entries.".truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            return
        }

        val slice = rows.drop(collectionScrollOffset).take(layout.maxDataRows)
        slice.forEachIndexed { visualIndex, row ->
            val globalIndex = collectionScrollOffset + visualIndex
            val selected = globalIndex == collectionSelectedIndex
            val prefix = if (selected) "> " else "  "
            context.putText(
                column = panelRect.left + 3,
                row = startRow + visualIndex,
                text = (prefix + formatStreamEntryRow(row)).truncate(VALUE_LINE_WIDTH),
                foregroundColor = if (selected) context.theme.success else context.theme.value,
                backgroundColor = context.theme.panel,
                bold = selected,
            )
        }

        clearPanelLine(context, panelRect, layout.hintRow)
        val tailParts = mutableListOf<String>()
        val lc = liveCollection
        if (lc != null && collectionStillBehindRedis(summary, rows.size, lc)) {
            summary?.let { s ->
                if (rows.size < s.memberCount) {
                    tailParts += "${rows.size} / ${s.memberCount} entries loaded"
                }
            }
            if (collectionScrollOffset > 0 || rows.size > layout.maxDataRows) {
                tailParts += "view ${collectionScrollOffset + 1}-${collectionScrollOffset + slice.size} of ${rows.size}"
            }
        }
        if (tailParts.isNotEmpty()) {
            context.putText(
                column = panelRect.left + 3,
                row = layout.hintRow,
                text = tailParts.joinToString(separator = " · ").truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
        }

        clearPanelLine(context, panelRect, layout.loadMoreRow)
        val buffer = liveCollection
        if (buffer != null && buffer.canLoadMore()) {
            val loadSelected = collectionSelectedIndex == rows.size
            val label = when {
                loadMoreInProgress -> "Loading next page…"
                loadSelected -> "> Load next page (Enter)"
                else -> "  Load next page (Enter)"
            }
            context.putText(
                column = panelRect.left + 3,
                row = layout.loadMoreRow,
                text = label.truncate(VALUE_LINE_WIDTH),
                foregroundColor = when {
                    loadMoreInProgress -> context.theme.hint
                    loadSelected -> context.theme.success
                    else -> context.theme.hint
                },
                backgroundColor = context.theme.panel,
                bold = loadSelected && !loadMoreInProgress,
            )
        }
    }

    private fun formatStreamEntryRow(row: RedisStreamEntryRow): String {
        val idPart = row.entryId.take(STREAM_ID_MAX_CHARS).padEnd(STREAM_ID_MAX_CHARS)
        val cells = row.fields.take(STREAM_FIELDS_INLINE_MAX)
        val renderedFields = cells.joinToString(separator = ", ") { cell ->
            val keyPart = cell.field.singleLinePreview().take(STREAM_FIELD_NAME_MAX_CHARS)
            val valuePart = formatInlineValuePreview(cell.valuePreview).truncate(STREAM_FIELD_VALUE_CHARS)
            "$keyPart=$valuePart"
        }
        val ellipsis = if (row.fields.size > STREAM_FIELDS_INLINE_MAX) ", …" else ""
        return "$idPart  $renderedFields$ellipsis"
    }

    private fun drawSortedSetEntryRows(
        context: TuiContext,
        panelRect: TuiRect,
        snapshot: RedisKeyDetailSnapshot,
        summary: RedisCollectionSizeSummary.Known?,
    ) {
        val preview = snapshot.sortedSetEntriesPreview
        val startRow = collectionValueStartRow(snapshot, panelRect)
        val layout = collectionViewportLayout(panelRect, startRow)
        val buf = liveCollection as? LiveCollectionBuffer.ZSetBuf
        val rows = buf?.rows ?: preview?.rows ?: emptyList()

        if (preview == null) {
            context.putText(
                column = panelRect.left + 3,
                row = startRow,
                text = "Could not load sorted set entries.".truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.warning,
                backgroundColor = context.theme.panel,
            )
            return
        }

        if (rows.isEmpty()) {
            context.putText(
                column = panelRect.left + 3,
                row = startRow,
                text = "Sorted set is empty.".truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            return
        }

        val slice = rows.drop(collectionScrollOffset).take(layout.maxDataRows)
        slice.forEachIndexed { visualIndex, row ->
            val globalIndex = collectionScrollOffset + visualIndex
            val selected = globalIndex == collectionSelectedIndex
            val prefix = if (selected) "> " else "  "
            context.putText(
                column = panelRect.left + 3,
                row = startRow + visualIndex,
                text = (prefix + formatSortedSetEntryRow(row)).truncate(VALUE_LINE_WIDTH),
                foregroundColor = if (selected) context.theme.success else context.theme.value,
                backgroundColor = context.theme.panel,
                bold = selected,
            )
        }

        clearPanelLine(context, panelRect, layout.hintRow)
        val tailParts = mutableListOf<String>()
        val lc = liveCollection
        if (lc != null && collectionStillBehindRedis(summary, rows.size, lc)) {
            summary?.let { s ->
                if (rows.size < s.memberCount) {
                    tailParts += "${rows.size} / ${s.memberCount} members loaded"
                }
            }
            if (collectionScrollOffset > 0 || rows.size > layout.maxDataRows) {
                tailParts += "view ${collectionScrollOffset + 1}-${collectionScrollOffset + slice.size} of ${rows.size}"
            }
        }
        if (tailParts.isNotEmpty()) {
            context.putText(
                column = panelRect.left + 3,
                row = layout.hintRow,
                text = tailParts.joinToString(separator = " · ").truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
        }

        clearPanelLine(context, panelRect, layout.loadMoreRow)
        val buffer = liveCollection
        if (buffer != null && buffer.canLoadMore()) {
            val loadSelected = collectionSelectedIndex == rows.size
            val label = when {
                loadMoreInProgress -> "Loading next page…"
                loadSelected -> "> Load next page (Enter)"
                else -> "  Load next page (Enter)"
            }
            context.putText(
                column = panelRect.left + 3,
                row = layout.loadMoreRow,
                text = label.truncate(VALUE_LINE_WIDTH),
                foregroundColor = when {
                    loadMoreInProgress -> context.theme.hint
                    loadSelected -> context.theme.success
                    else -> context.theme.hint
                },
                backgroundColor = context.theme.panel,
                bold = loadSelected && !loadMoreInProgress,
            )
        }
    }

    private fun formatSortedSetEntryRow(row: RedisSortedSetEntryRow): String {
        val scorePart = formatSortedSetScore(row.score)
        val memberPart = formatInlineValuePreview(row.memberPreview).truncate(ZSET_MEMBER_VALUE_CHARS)
        return "$scorePart  $memberPart"
    }

    private fun formatSortedSetScore(score: Double): String {
        val text = when {
            score.isNaN() -> "nan"
            score == Double.POSITIVE_INFINITY -> "+inf"
            score == Double.NEGATIVE_INFINITY -> "-inf"
            score == score.toLong().toDouble() -> score.toLong().toString()
            else -> score.toString()
        }
        return text.take(ZSET_SCORE_COLUMN_CHARS).padEnd(ZSET_SCORE_COLUMN_CHARS)
    }

    private fun drawSetMemberRows(
        context: TuiContext,
        panelRect: TuiRect,
        snapshot: RedisKeyDetailSnapshot,
        summary: RedisCollectionSizeSummary.Known?,
    ) {
        val preview = snapshot.setMembersPreview
        val startRow = collectionValueStartRow(snapshot, panelRect)
        val layout = collectionViewportLayout(panelRect, startRow)
        val buf = liveCollection as? LiveCollectionBuffer.SetBuf
        val rows = buf?.rows ?: preview?.rows ?: emptyList()

        if (preview == null) {
            context.putText(
                column = panelRect.left + 3,
                row = startRow,
                text = "Could not load set members.".truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.warning,
                backgroundColor = context.theme.panel,
            )
            return
        }

        if (rows.isEmpty()) {
            context.putText(
                column = panelRect.left + 3,
                row = startRow,
                text = "Set is empty.".truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            return
        }

        val slice = rows.drop(collectionScrollOffset).take(layout.maxDataRows)
        slice.forEachIndexed { visualIndex, row ->
            val globalIndex = collectionScrollOffset + visualIndex
            val selected = globalIndex == collectionSelectedIndex
            val prefix = if (selected) "> " else "  "
            context.putText(
                column = panelRect.left + 3,
                row = startRow + visualIndex,
                text = (prefix + formatSetMemberRow(row)).truncate(VALUE_LINE_WIDTH),
                foregroundColor = if (selected) context.theme.success else context.theme.value,
                backgroundColor = context.theme.panel,
                bold = selected,
            )
        }

        clearPanelLine(context, panelRect, layout.hintRow)
        val tailParts = mutableListOf<String>()
        val lc = liveCollection
        if (lc != null && collectionStillBehindRedis(summary, rows.size, lc)) {
            summary?.let { s ->
                if (rows.size < s.memberCount) {
                    tailParts += "${rows.size} / ${s.memberCount} members loaded"
                }
            }
            if (collectionScrollOffset > 0 || rows.size > layout.maxDataRows) {
                tailParts += "view ${collectionScrollOffset + 1}-${collectionScrollOffset + slice.size} of ${rows.size}"
            }
        }
        if (tailParts.isNotEmpty()) {
            context.putText(
                column = panelRect.left + 3,
                row = layout.hintRow,
                text = tailParts.joinToString(separator = " · ").truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
        }

        clearPanelLine(context, panelRect, layout.loadMoreRow)
        val buffer = liveCollection
        if (buffer != null && buffer.canLoadMore()) {
            val loadSelected = collectionSelectedIndex == rows.size
            val label = when {
                loadMoreInProgress -> "Loading next page…"
                loadSelected -> "> Load next page (Enter)"
                else -> "  Load next page (Enter)"
            }
            context.putText(
                column = panelRect.left + 3,
                row = layout.loadMoreRow,
                text = label.truncate(VALUE_LINE_WIDTH),
                foregroundColor = when {
                    loadMoreInProgress -> context.theme.hint
                    loadSelected -> context.theme.success
                    else -> context.theme.hint
                },
                backgroundColor = context.theme.panel,
                bold = loadSelected && !loadMoreInProgress,
            )
        }
    }

    private fun formatSetMemberRow(row: RedisSetMemberRow): String {
        return "- ${formatInlineValuePreview(row.valuePreview).truncate(SET_MEMBER_VALUE_CHARS)}"
    }

    private fun drawListItemRows(
        context: TuiContext,
        panelRect: TuiRect,
        snapshot: RedisKeyDetailSnapshot,
        summary: RedisCollectionSizeSummary.Known?,
    ) {
        val preview = snapshot.listItemsPreview
        val startRow = collectionValueStartRow(snapshot, panelRect)
        val layout = collectionViewportLayout(panelRect, startRow)
        val buf = liveCollection as? LiveCollectionBuffer.ListBuf
        val rows = buf?.rows ?: preview?.rows ?: emptyList()

        if (preview == null) {
            context.putText(
                column = panelRect.left + 3,
                row = startRow,
                text = "Could not load list elements.".truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.warning,
                backgroundColor = context.theme.panel,
            )
            return
        }

        if (rows.isEmpty()) {
            context.putText(
                column = panelRect.left + 3,
                row = startRow,
                text = "List is empty.".truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            return
        }

        val slice = rows.drop(collectionScrollOffset).take(layout.maxDataRows)
        slice.forEachIndexed { visualIndex, row ->
            val globalIndex = collectionScrollOffset + visualIndex
            val selected = globalIndex == collectionSelectedIndex
            val prefix = if (selected) "> " else "  "
            context.putText(
                column = panelRect.left + 3,
                row = startRow + visualIndex,
                text = (prefix + formatListItemRow(row)).truncate(VALUE_LINE_WIDTH),
                foregroundColor = if (selected) context.theme.success else context.theme.value,
                backgroundColor = context.theme.panel,
                bold = selected,
            )
        }

        clearPanelLine(context, panelRect, layout.hintRow)
        val tailParts = mutableListOf<String>()
        val lc = liveCollection
        if (lc != null && collectionStillBehindRedis(summary, rows.size, lc)) {
            summary?.let { s ->
                if (rows.size < s.memberCount) {
                    tailParts += "${rows.size} / ${s.memberCount} elements loaded"
                }
            }
            if (collectionScrollOffset > 0 || rows.size > layout.maxDataRows) {
                tailParts += "view ${collectionScrollOffset + 1}-${collectionScrollOffset + slice.size} of ${rows.size}"
            }
        }
        if (tailParts.isNotEmpty()) {
            context.putText(
                column = panelRect.left + 3,
                row = layout.hintRow,
                text = tailParts.joinToString(separator = " · ").truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
        }

        clearPanelLine(context, panelRect, layout.loadMoreRow)
        val buffer = liveCollection
        if (buffer != null && buffer.canLoadMore()) {
            val loadSelected = collectionSelectedIndex == rows.size
            val label = when {
                loadMoreInProgress -> "Loading next page…"
                loadSelected -> "> Load next page (Enter)"
                else -> "  Load next page (Enter)"
            }
            context.putText(
                column = panelRect.left + 3,
                row = layout.loadMoreRow,
                text = label.truncate(VALUE_LINE_WIDTH),
                foregroundColor = when {
                    loadMoreInProgress -> context.theme.hint
                    loadSelected -> context.theme.success
                    else -> context.theme.hint
                },
                backgroundColor = context.theme.panel,
                bold = loadSelected && !loadMoreInProgress,
            )
        }
    }

    private fun formatListItemRow(row: RedisListItemRow): String {
        val idxPart = "[${row.index}]".padEnd(8)
        val valuePart = formatInlineValuePreview(row.valuePreview)
        return "$idxPart ${valuePart.truncate(LIST_VALUE_TAIL_CHARS)}"
    }

    private fun drawHashFieldRows(
        context: TuiContext,
        panelRect: TuiRect,
        snapshot: RedisKeyDetailSnapshot,
        summary: RedisCollectionSizeSummary.Known?,
    ) {
        val preview = snapshot.hashFieldsPreview
        val startRow = collectionValueStartRow(snapshot, panelRect)
        val layout = collectionViewportLayout(panelRect, startRow)
        val buf = liveCollection as? LiveCollectionBuffer.HashBuf
        val rows = buf?.rows ?: preview?.rows ?: emptyList()

        if (preview == null) {
            context.putText(
                column = panelRect.left + 3,
                row = startRow,
                text = "Could not load hash fields.".truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.warning,
                backgroundColor = context.theme.panel,
            )
            return
        }

        if (rows.isEmpty()) {
            context.putText(
                column = panelRect.left + 3,
                row = startRow,
                text = "Hash has no fields.".truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
            return
        }

        val slice = rows.drop(collectionScrollOffset).take(layout.maxDataRows)
        slice.forEachIndexed { visualIndex, row ->
            val globalIndex = collectionScrollOffset + visualIndex
            val selected = globalIndex == collectionSelectedIndex
            val prefix = if (selected) "> " else "  "
            context.putText(
                column = panelRect.left + 3,
                row = startRow + visualIndex,
                text = (prefix + formatHashFieldRow(row)).truncate(VALUE_LINE_WIDTH),
                foregroundColor = if (selected) context.theme.success else context.theme.value,
                backgroundColor = context.theme.panel,
                bold = selected,
            )
        }

        clearPanelLine(context, panelRect, layout.hintRow)
        val tailParts = mutableListOf<String>()
        val lc = liveCollection
        if (lc != null && collectionStillBehindRedis(summary, rows.size, lc)) {
            summary?.let { s ->
                if (rows.size < s.memberCount) {
                    tailParts += "${rows.size} / ${s.memberCount} fields loaded"
                }
            }
            if (collectionScrollOffset > 0 || rows.size > layout.maxDataRows) {
                tailParts += "view ${collectionScrollOffset + 1}-${collectionScrollOffset + slice.size} of ${rows.size}"
            }
        }
        if (tailParts.isNotEmpty()) {
            context.putText(
                column = panelRect.left + 3,
                row = layout.hintRow,
                text = tailParts.joinToString(separator = " · ").truncate(VALUE_LINE_WIDTH),
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
        }

        clearPanelLine(context, panelRect, layout.loadMoreRow)
        val buffer = liveCollection
        if (buffer != null && buffer.canLoadMore()) {
            val loadSelected = collectionSelectedIndex == rows.size
            val label = when {
                loadMoreInProgress -> "Loading next page…"
                loadSelected -> "> Load next page (Enter)"
                else -> "  Load next page (Enter)"
            }
            context.putText(
                column = panelRect.left + 3,
                row = layout.loadMoreRow,
                text = label.truncate(VALUE_LINE_WIDTH),
                foregroundColor = when {
                    loadMoreInProgress -> context.theme.hint
                    loadSelected -> context.theme.success
                    else -> context.theme.hint
                },
                backgroundColor = context.theme.panel,
                bold = loadSelected && !loadMoreInProgress,
            )
        }
    }

    private fun formatHashFieldRow(row: RedisHashFieldRow): String {
        val fieldPart = row.field.singleLinePreview().truncate(HASH_FIELD_COLUMN_CHARS)
        val valuePart = formatInlineValuePreview(row.valuePreview)
        return "$fieldPart -> ${valuePart.truncate(HASH_VALUE_COLUMN_CHARS)}"
    }

    private fun formatInlineValuePreview(preview: RedisStringValuePreview): String {
        return when (preview) {
            is RedisStringValuePreview.Text -> preview.value.singleLinePreview()
            is RedisStringValuePreview.Json -> "json ${preview.value.singleLinePreview()}"
            is RedisStringValuePreview.Binary ->
                "binary ${preview.sizeBytes} B ${preview.hexPreview}"
        }
    }

    private fun drawLoadedValuePreview(
        context: TuiContext,
        panelRect: TuiRect,
        snapshot: RedisKeyDetailSnapshot,
    ) {
        if (snapshot.key.type != RedisKeyType.STRING) {
            drawNonStringCollection(context, panelRect, snapshot)
            return
        }

        val preview = snapshot.stringValuePreview
        if (preview == null) {
            context.putText(
                column = panelRect.left + 3,
                row = panelRect.top + 11,
                text = "STRING value is missing or key changed while loading.",
                foregroundColor = context.theme.warning,
                backgroundColor = context.theme.panel,
            )
            return
        }

        when (preview) {
            is RedisStringValuePreview.Text -> {
                drawStringValue(context, panelRect, label = "Value", value = preview.value)
            }

            is RedisStringValuePreview.Json -> {
                context.putText(
                    column = panelRect.left + 3,
                    row = panelRect.top + 9,
                    text = "JSON auto-detected · preview: compact · raw (v): pretty-printed".truncate(VALUE_LINE_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
                drawJsonStringValue(context, panelRect, raw = preview.value)
            }

            is RedisStringValuePreview.Binary -> {
                context.putText(
                    column = panelRect.left + 3,
                    row = panelRect.top + 9,
                    text = "Binary value · preview: short hex · raw (v): hex dump".truncate(VALUE_LINE_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
                drawBinaryValue(context, panelRect, preview)
            }
        }
    }

    private fun drawBinaryValue(
        context: TuiContext,
        panelRect: TuiRect,
        preview: RedisStringValuePreview.Binary,
    ) {
        drawValueModeLabel(context, panelRect)

        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 11,
            text = "Binary value detected (${preview.sizeBytes} bytes).".truncate(VALUE_LINE_WIDTH),
            foregroundColor = context.theme.warning,
            backgroundColor = context.theme.panel,
            bold = true,
        )

        when (valueViewMode) {
            ValueViewMode.Preview -> {
                context.putText(
                    column = panelRect.left + 3,
                    row = panelRect.top + 12,
                    text = "Hex preview: ${preview.hexPreview}".truncate(VALUE_LINE_WIDTH),
                    foregroundColor = context.theme.hint,
                    backgroundColor = context.theme.panel,
                )
            }

            ValueViewMode.Raw -> {
                val lines = preview.hexDumpLines
                val visible = lines.take(MAX_BINARY_HEX_RAW_LINES)
                visible.forEachIndexed { index, line ->
                    context.putText(
                        column = panelRect.left + 3,
                        row = panelRect.top + 12 + index,
                        text = line.truncate(VALUE_LINE_WIDTH),
                        foregroundColor = context.theme.value,
                        backgroundColor = context.theme.panel,
                    )
                }

                val truncationParts = mutableListOf<String>()
                if (lines.size > visible.size) {
                    truncationParts += "${lines.size - visible.size} more line(s) in sample"
                }
                if (preview.sizeBytes > preview.hexDumpSampleBytes) {
                    truncationParts += "sample ends at byte ${preview.hexDumpSampleBytes} of ${preview.sizeBytes}"
                }

                if (truncationParts.isNotEmpty()) {
                    context.putText(
                        column = panelRect.left + 3,
                        row = panelRect.top + 12 + visible.size,
                        text = truncationParts.joinToString(separator = " · ").truncate(VALUE_LINE_WIDTH),
                        foregroundColor = context.theme.hint,
                        backgroundColor = context.theme.panel,
                    )
                }
            }
        }
    }

    private fun jsonFormats(raw: String): JsonPrettyFormatter.Result {
        if (cachedJsonSource == raw && cachedJsonFormats != null) {
            return cachedJsonFormats!!
        }

        val formatted = JsonPrettyFormatter.format(raw)
        cachedJsonSource = raw
        cachedJsonFormats = formatted
        return formatted
    }

    private fun drawJsonStringValue(
        context: TuiContext,
        panelRect: TuiRect,
        raw: String,
    ) {
        val formatted = jsonFormats(raw)

        drawValueModeLabel(context, panelRect)

        when (valueViewMode) {
            ValueViewMode.Preview -> drawStringPreview(
                context = context,
                panelRect = panelRect,
                value = "JSON: ${formatted.compact.singleLinePreview()}",
            )

            ValueViewMode.Raw -> drawRawStringValue(
                context = context,
                panelRect = panelRect,
                label = "JSON",
                value = formatted.pretty,
                maxLines = MAX_JSON_RAW_LINES,
            )
        }
    }

    private fun drawStringValue(
        context: TuiContext,
        panelRect: TuiRect,
        label: String,
        value: String,
    ) {
        drawValueModeLabel(context, panelRect)

        when (valueViewMode) {
            ValueViewMode.Preview -> drawStringPreview(
                context = context,
                panelRect = panelRect,
                value = "$label: ${value.singleLinePreview()}",
            )

            ValueViewMode.Raw -> drawRawStringValue(
                context = context,
                panelRect = panelRect,
                label = label,
                value = value,
            )
        }
    }

    private fun drawValueModeLabel(
        context: TuiContext,
        panelRect: TuiRect,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 10,
            text = "View: ${valueViewMode.label}",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
            bold = true,
        )
    }

    private fun drawStringPreview(
        context: TuiContext,
        panelRect: TuiRect,
        value: String,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 11,
            text = value.truncate(VALUE_LINE_WIDTH),
            foregroundColor = context.theme.value,
            backgroundColor = context.theme.panel,
        )
    }

    private fun drawRawStringValue(
        context: TuiContext,
        panelRect: TuiRect,
        label: String,
        value: String,
        maxLines: Int = MAX_RAW_VALUE_LINES,
    ) {
        context.putText(
            column = panelRect.left + 3,
            row = panelRect.top + 11,
            text = "$label raw:",
            foregroundColor = context.theme.label,
            backgroundColor = context.theme.panel,
        )

        val rawLines = value.rawDisplayLines()
        rawLines
            .take(maxLines)
            .forEachIndexed { index, line ->
                context.putText(
                    column = panelRect.left + 3,
                    row = panelRect.top + 12 + index,
                    text = line.truncate(VALUE_LINE_WIDTH),
                    foregroundColor = context.theme.value,
                    backgroundColor = context.theme.panel,
                )
            }

        if (rawLines.size > maxLines) {
            context.putText(
                column = panelRect.left + 3,
                row = panelRect.top + 12 + maxLines,
                text = "... ${rawLines.size - maxLines} more line(s)",
                foregroundColor = context.theme.hint,
                backgroundColor = context.theme.panel,
            )
        }
    }

    private fun toggleValueViewMode() {
        valueViewMode = when (valueViewMode) {
            ValueViewMode.Preview -> ValueViewMode.Raw
            ValueViewMode.Raw -> ValueViewMode.Preview
        }
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
        expired: Boolean,
    ): TextColor {
        return when {
            expired -> context.theme.danger
            keySummary.ttl == RedisKeyTtlStatus.NoExpiration -> context.theme.warning
            else -> context.theme.value
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

    private fun String.singleLinePreview(): String {
        return replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }

    private fun String.rawDisplayLines(): List<String> {
        return replace("\r\n", "\n")
            .replace("\r", "\\r")
            .split("\n")
            .map { line -> line.ifEmpty { "<empty line>" } }
    }

    private fun isBackKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.Escape || isCharacter(keyStroke, 'b')
    }

    private fun isExitKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.EOF || isCharacter(keyStroke, 'q')
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
            take(maxLength - 1) + "~"
        }
    }

    private sealed interface KeyDetailState {
        data object Loading : KeyDetailState

        data class Loaded(
            val snapshot: RedisKeyDetailSnapshot,
        ) : KeyDetailState

        data class Error(
            val message: String,
        ) : KeyDetailState
    }

    private enum class ValueViewMode(
        val label: String,
        val nextActionLabel: String,
    ) {
        Preview("preview", "raw view"),
        Raw("raw", "preview"),
    }

    private enum class KeyDetailInputMode {
        Browse,
        ExpireSeconds,
        DeleteConfirmation,
    }

    private sealed interface DeleteTarget {
        data object None : DeleteTarget

        data class Key(
            val previewLines: List<String>,
        ) : DeleteTarget

        data class Value(
            val target: DeleteKeyValueTarget,
            val subject: String,
            val previewLines: List<String>,
        ) : DeleteTarget

        fun confirmationToast(): KeyOperationMessage {
            return when (this) {
                is Key -> KeyOperationMessage.Failure("Delete this key?")
                is Value -> KeyOperationMessage.Failure("Delete selected $subject?")
                None -> KeyOperationMessage.Info("No value selected.")
            }
        }

        fun inProgressToast(): KeyOperationMessage {
            return when (this) {
                is Key -> KeyOperationMessage.Info("Deleting key...")
                is Value -> KeyOperationMessage.Info("Deleting $subject...")
                None -> KeyOperationMessage.Info("Deleting...")
            }
        }

        fun successToast(): KeyOperationMessage {
            return when (this) {
                is Key -> KeyOperationMessage.Success("Key deleted.")
                is Value -> KeyOperationMessage.Success("${subject.replaceFirstChar { it.uppercaseChar() }} deleted.")
                None -> KeyOperationMessage.Success("Deleted.")
            }
        }

        fun missingToast(): KeyOperationMessage {
            return when (this) {
                is Key -> KeyOperationMessage.Failure("Key no longer exists.")
                is Value -> KeyOperationMessage.Failure("${subject.replaceFirstChar { it.uppercaseChar() }} no longer exists.")
                None -> KeyOperationMessage.Failure("Value no longer exists.")
            }
        }

        fun previewLines(): List<String> {
            return when (this) {
                is Key -> previewLines
                is Value -> previewLines
                None -> emptyList()
            }
        }

        fun auditAction(): String {
            return when (this) {
                is Key -> "delete-key"
                is Value -> "delete-key-value"
                None -> "delete-selection"
            }
        }

        fun auditTarget(): String {
            return when (this) {
                is Key -> "key"
                is Value -> when (val valueTarget = target) {
                    is DeleteKeyValueTarget.HashField -> "hash-field:${valueTarget.field}"
                    is DeleteKeyValueTarget.ListItem -> "list-index:${valueTarget.index}"
                    is DeleteKeyValueTarget.SetMember -> "set-member:<redacted>"
                    is DeleteKeyValueTarget.SortedSetMember -> "zset-member:<redacted>"
                    is DeleteKeyValueTarget.StreamEntry -> "stream-entry:${valueTarget.entryId}"
                }
                None -> "selected-value"
            }
        }
    }

    private enum class DeleteTargetResult {
        Deleted,
        Missing,
    }

    private companion object {
        const val HASH_PAGE_SIZE = 32
        const val SET_PAGE_SIZE = 32
        const val ZSET_PAGE_SIZE = 32
        const val EXPIRED_GRACE_MILLIS = 10_000L
        const val VALUE_LINE_WIDTH = 76
        const val MAX_EXPIRE_SECONDS_DIGITS = 10
        const val MAX_RAW_VALUE_LINES = 5
        const val MAX_JSON_RAW_LINES = 8
        const val MAX_BINARY_HEX_RAW_LINES = 7
        const val HASH_FIELD_COLUMN_CHARS = 22
        const val HASH_VALUE_COLUMN_CHARS = 48
        const val LIST_VALUE_TAIL_CHARS = 62
        const val SET_MEMBER_VALUE_CHARS = 70
        const val ZSET_SCORE_COLUMN_CHARS = 14
        const val ZSET_MEMBER_VALUE_CHARS = 56
        const val STREAM_ID_MAX_CHARS = 18
        const val STREAM_FIELDS_INLINE_MAX = 4
        const val STREAM_FIELD_NAME_MAX_CHARS = 12
        const val STREAM_FIELD_VALUE_CHARS = 24
    }
}
