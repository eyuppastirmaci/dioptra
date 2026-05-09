package io.github.eyuppastirmaci.dioptra.bootstrap

import io.github.eyuppastirmaci.dioptra.application.commandstats.LoadCommandStatsUseCase
import io.github.eyuppastirmaci.dioptra.application.latency.LoadLatencyStatsUseCase
import io.github.eyuppastirmaci.dioptra.application.dashboard.LoadDashboardUseCase
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysUseCase
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyUseCase
import io.github.eyuppastirmaci.dioptra.application.key.DeleteKeyValueUseCase
import io.github.eyuppastirmaci.dioptra.application.key.ExpireKeyUseCase
import io.github.eyuppastirmaci.dioptra.application.key.LoadKeyDetailUseCase
import io.github.eyuppastirmaci.dioptra.application.namespace.LoadNamespaceAnalysisUseCase
import io.github.eyuppastirmaci.dioptra.application.namespace.LoadNamespaceDetailUseCase
import io.github.eyuppastirmaci.dioptra.application.namespace.NamespaceAnalysisEngine
import io.github.eyuppastirmaci.dioptra.application.namespace.NamespaceHealthScorer
import io.github.eyuppastirmaci.dioptra.application.namespace.NamespaceResolver
import io.github.eyuppastirmaci.dioptra.application.namespace.SaveNamespaceAnalysisSettingsUseCase
import io.github.eyuppastirmaci.dioptra.application.safety.OperationAuditContext
import io.github.eyuppastirmaci.dioptra.application.safety.OperationAuditLogger
import io.github.eyuppastirmaci.dioptra.application.safety.ProtectedNamespaceRules
import io.github.eyuppastirmaci.dioptra.cli.CliOptions
import io.github.eyuppastirmaci.dioptra.config.ConnectionResolution
import io.github.eyuppastirmaci.dioptra.config.ConnectionResolver
import io.github.eyuppastirmaci.dioptra.config.HoconConnectionProfileStore
import io.github.eyuppastirmaci.dioptra.config.HoconLastUsedConnectionStore
import io.github.eyuppastirmaci.dioptra.config.LastUsedConnectionMetadata
import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisConnectionManager
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisHealthClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisInfoClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyBrowserClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyDetailClient
import io.github.eyuppastirmaci.dioptra.application.slowlog.LoadSlowlogUseCase
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisCommandStatsClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisLatencyStatsClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyOperationClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisSlowlogClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisMemoryUsageMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTtlMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTypeMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.codec.Utf8ValueDecoder
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisInfoParser
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisKeyspaceParser
import io.github.eyuppastirmaci.dioptra.presentation.tui.TuiApplication
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.ByteSizeFormatter
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeyBrowserSorter
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeyMemoryUsageFormatter
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeyRiskClassifier
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.RedisKeyTtlFormatter
import io.github.eyuppastirmaci.dioptra.presentation.tui.format.TextTruncator
import io.github.eyuppastirmaci.dioptra.presentation.tui.input.TuiKeyMatcher
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.ConnectionAttemptResult
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.ConnectionScreen
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.DashboardScreen
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.TuiScreen
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.keybrowser.KeyBrowserRenderer
import io.github.eyuppastirmaci.dioptra.presentation.tui.terminal.TerminalFactoryProvider
import org.slf4j.LoggerFactory

class ApplicationBootstrap {

    private val logger = LoggerFactory.getLogger(ApplicationBootstrap::class.java)
    private val lastUsedConnectionStore = HoconLastUsedConnectionStore()
    private val activeConnectionManagers = mutableListOf<RedisConnectionManager>()

    fun start(cliOptions: CliOptions = CliOptions()) {
        val terminalFactory = TerminalFactoryProvider.create()
        val tuiApplication = TuiApplication(terminalFactory)
        val resolution = ConnectionResolver().resolve(cliOptions.connection)
        val protectedNamespaceRules = ProtectedNamespaceRules(cliOptions.protectedNamespaces)

        try {
            tuiApplication.run(
                initialScreenFor(
                    resolution = resolution,
                    readOnly = cliOptions.readOnly,
                    productionSafety = cliOptions.productionSafety,
                    protectedNamespaceRules = protectedNamespaceRules,
                )
            )
        } finally {
            activeConnectionManagers.forEach { it.close() }
            activeConnectionManagers.clear()
        }
    }

    private fun initialScreenFor(
        resolution: ConnectionResolution,
        readOnly: Boolean,
        productionSafety: Boolean,
        protectedNamespaceRules: ProtectedNamespaceRules,
    ): TuiScreen {
        return when (resolution) {
            is ConnectionResolution.Ready -> {
                logger.info(
                    "Resolved Redis connection from {}: {}",
                    resolution.source,
                    resolution.config.maskedUri,
                )
                when (
                    val attempt = connectToDashboard(
                        config = resolution.config,
                        readOnly = readOnly,
                        productionSafety = productionSafety,
                        protectedNamespaceRules = protectedNamespaceRules,
                    )
                ) {
                    is ConnectionAttemptResult.Success -> attempt.nextScreen
                    is ConnectionAttemptResult.Failure -> connectionScreen(
                        initialConfig = resolution.config,
                        initialMessage = attempt.message,
                        readOnly = readOnly,
                        productionSafety = productionSafety,
                        protectedNamespaceRules = protectedNamespaceRules,
                    )
                }
            }

            is ConnectionResolution.NeedsUserInput -> {
                val fallbackConfig = resolution.partialConfig ?: RedisConnectionConfig()
                logger.warn(
                    "Redis connection needs user input: {}. Opening ConnectionScreen with partial config: {}",
                    resolution.reason,
                    fallbackConfig.maskedUri,
                )
                connectionScreen(
                    initialConfig = fallbackConfig,
                    initialMessage = resolution.reason,
                    readOnly = readOnly,
                    productionSafety = productionSafety,
                    protectedNamespaceRules = protectedNamespaceRules,
                )
            }
        }
    }

    private fun connectionScreen(
        initialConfig: RedisConnectionConfig,
        initialMessage: String?,
        readOnly: Boolean,
        productionSafety: Boolean,
        protectedNamespaceRules: ProtectedNamespaceRules,
    ): ConnectionScreen {
        return ConnectionScreen(
            profileStore = HoconConnectionProfileStore(),
            lastUsedConnectionStore = lastUsedConnectionStore,
            initialConfig = initialConfig,
            initialMessage = initialMessage,
            connect = { config ->
                connectToDashboard(
                    config = config,
                    readOnly = readOnly,
                    productionSafety = productionSafety,
                    protectedNamespaceRules = protectedNamespaceRules,
                )
            },
        )
    }

    private fun connectToDashboard(
        config: RedisConnectionConfig,
        readOnly: Boolean,
        productionSafety: Boolean,
        protectedNamespaceRules: ProtectedNamespaceRules,
    ): ConnectionAttemptResult {
        var redisConnectionManager: RedisConnectionManager? = null

        return runCatching {
            val activeRedisConnectionManager = RedisConnectionManager(config)
            redisConnectionManager = activeRedisConnectionManager
            activeRedisConnectionManager.connect()
            activeConnectionManagers.add(activeRedisConnectionManager)
            saveLastUsedConnection(config)

            ConnectionAttemptResult.Success(
                nextScreen = createDashboardScreen(
                    redisConnectionManager = activeRedisConnectionManager,
                    readOnly = readOnly,
                    productionSafety = productionSafety,
                    protectedNamespaceRules = protectedNamespaceRules,
                ),
                close = {
                    activeRedisConnectionManager.close()
                    activeConnectionManagers.remove(activeRedisConnectionManager)
                },
            )
        }.getOrElse { exception ->
            redisConnectionManager?.let {
                activeConnectionManagers.remove(it)
                it.close()
            }
            logger.warn("Could not connect to Redis at {}.", config.maskedUri, exception)
            ConnectionAttemptResult.Failure(
                message = "Connection failed: ${UserFacingErrorMessage.from(exception)}",
            )
        }
    }

    private fun createDashboardScreen(
        redisConnectionManager: RedisConnectionManager,
        readOnly: Boolean,
        productionSafety: Boolean,
        protectedNamespaceRules: ProtectedNamespaceRules,
    ): DashboardScreen {
        val redisCommands = redisConnectionManager.syncCommands()
        val redisBinaryValueCommands = redisConnectionManager.syncBinaryValueCommands()

        val redisHealthClient = RedisHealthClient(redisCommands)
        val redisInfoClient = RedisInfoClient(redisCommands)
        val redisSlowlogClient = RedisSlowlogClient(redisCommands)
        val redisCommandStatsClient = RedisCommandStatsClient(redisCommands)
        val redisLatencyStatsClient = RedisLatencyStatsClient(redisCommands)
        val redisKeyBrowserClient = RedisKeyBrowserClient(redisCommands)
        val redisKeyDetailClient = RedisKeyDetailClient(redisBinaryValueCommands)
        val redisKeyOperationClient = RedisKeyOperationClient(
            commands = redisCommands,
            binaryValueCommands = redisBinaryValueCommands,
        )

        val redisInfoParser = RedisInfoParser()
        val redisKeyspaceParser = RedisKeyspaceParser()

        val redisTypeMapper = RedisTypeMapper()
        val redisTtlMapper = RedisTtlMapper()
        val redisMemoryUsageMapper = RedisMemoryUsageMapper()
        val redisValueDecoder = Utf8ValueDecoder()
        val keyBrowserSorter = RedisKeyBrowserSorter()
        val keyRiskClassifier = RedisKeyRiskClassifier()
        val keyBrowserRenderer = KeyBrowserRenderer(
            sorter = keyBrowserSorter,
            ttlFormatter = RedisKeyTtlFormatter(),
            memoryUsageFormatter = RedisKeyMemoryUsageFormatter(
                byteSizeFormatter = ByteSizeFormatter(),
                riskClassifier = keyRiskClassifier,
            ),
            riskClassifier = keyRiskClassifier,
            textTruncator = TextTruncator(),
        )
        val keyMatcher = TuiKeyMatcher()
        val operationAuditLogger = OperationAuditLogger(
            OperationAuditContext(
                profileName = redisConnectionManager.config.name,
                database = redisConnectionManager.config.database,
                maskedUri = redisConnectionManager.config.maskedUri,
                readOnly = readOnly,
                productionSafety = productionSafety,
            )
        )

        val loadDashboardUseCase = LoadDashboardUseCase(
            connectionConfig = redisConnectionManager.config,
            redisHealthClient = redisHealthClient,
            redisInfoClient = redisInfoClient,
            redisInfoParser = redisInfoParser,
            redisKeyspaceParser = redisKeyspaceParser,
        )

        val loadSlowlogUseCase = LoadSlowlogUseCase(
            redisSlowlogClient = redisSlowlogClient,
        )

        val loadCommandStatsUseCase = LoadCommandStatsUseCase(
            redisCommandStatsClient = redisCommandStatsClient,
        )

        val loadLatencyStatsUseCase = LoadLatencyStatsUseCase(
            redisLatencyStatsClient = redisLatencyStatsClient,
        )

        val namespaceAnalysisSettings = redisConnectionManager.config.namespaceAnalysisSettings
        val namespaceAnalysisUseCaseFactory = { settings: NamespaceAnalysisSettings ->
            val namespaceResolver = NamespaceResolver(
                settings = settings,
            )
            val namespaceHealthScorer = NamespaceHealthScorer()
            val namespaceAnalysisEngine = NamespaceAnalysisEngine(
                redisKeyBrowserClient = redisKeyBrowserClient,
                redisTtlMapper = redisTtlMapper,
                redisMemoryUsageMapper = redisMemoryUsageMapper,
                namespaceAnalysisSettings = settings,
                namespaceResolver = namespaceResolver,
                namespaceHealthScorer = namespaceHealthScorer,
            )
            Pair(
                LoadNamespaceAnalysisUseCase(namespaceAnalysisEngine = namespaceAnalysisEngine),
                LoadNamespaceDetailUseCase(namespaceAnalysisEngine = namespaceAnalysisEngine),
            )
        }
        val saveNamespaceAnalysisSettingsUseCase = SaveNamespaceAnalysisSettingsUseCase(
            profileStore = HoconConnectionProfileStore(),
            connectionConfig = redisConnectionManager.config,
        )

        val browseKeysUseCase = BrowseKeysUseCase(
            redisKeyBrowserClient = redisKeyBrowserClient,
            redisTypeMapper = redisTypeMapper,
            redisTtlMapper = redisTtlMapper,
            redisMemoryUsageMapper = redisMemoryUsageMapper,
        )

        val loadKeyDetailUseCase = LoadKeyDetailUseCase(
            redisKeyDetailClient = redisKeyDetailClient,
            redisValueDecoder = redisValueDecoder,
        )

        val expireKeyUseCase = ExpireKeyUseCase(
            redisKeyOperationClient = redisKeyOperationClient,
        )

        val deleteKeyUseCase = DeleteKeyUseCase(
            redisKeyOperationClient = redisKeyOperationClient,
        )

        val deleteKeyValueUseCase = DeleteKeyValueUseCase(
            redisKeyOperationClient = redisKeyOperationClient,
        )

        return DashboardScreen(
            snapshot = loadDashboardUseCase.load(),
            browseKeysUseCase = browseKeysUseCase,
            loadKeyDetailUseCase = loadKeyDetailUseCase,
            expireKeyUseCase = expireKeyUseCase,
            deleteKeyUseCase = deleteKeyUseCase,
            deleteKeyValueUseCase = deleteKeyValueUseCase,
            loadSlowlogUseCase = loadSlowlogUseCase,
            loadCommandStatsUseCase = loadCommandStatsUseCase,
            loadLatencyStatsUseCase = loadLatencyStatsUseCase,
            namespaceAnalysisUseCaseFactory = namespaceAnalysisUseCaseFactory,
            namespaceAnalysisSettings = namespaceAnalysisSettings,
            saveNamespaceAnalysisSettingsUseCase = saveNamespaceAnalysisSettingsUseCase,
            readOnly = readOnly,
            productionSafety = productionSafety,
            protectedNamespaceRules = protectedNamespaceRules,
            operationAuditLogger = operationAuditLogger,
            keyBrowserRenderer = keyBrowserRenderer,
            keyBrowserSorter = keyBrowserSorter,
            keyMatcher = keyMatcher,
            disconnect = {
                redisConnectionManager.close()
                activeConnectionManagers.remove(redisConnectionManager)
                connectionScreen(
                    initialConfig = RedisConnectionConfig(),
                    initialMessage = "Disconnected.",
                    readOnly = readOnly,
                    productionSafety = productionSafety,
                    protectedNamespaceRules = protectedNamespaceRules,
                )
            },
        )
    }

    private fun saveLastUsedConnection(config: RedisConnectionConfig) {
        runCatching {
            lastUsedConnectionStore.save(LastUsedConnectionMetadata.from(config))
        }.onFailure { exception ->
            logger.warn("Could not save last-used Redis connection metadata: {}", exception.message)
        }
    }
}
