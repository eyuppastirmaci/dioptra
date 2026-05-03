package io.github.eyuppastirmaci.dioptra.bootstrap

import io.github.eyuppastirmaci.dioptra.application.dashboard.LoadDashboardUseCase
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysUseCase
import io.github.eyuppastirmaci.dioptra.application.key.LoadKeyDetailUseCase
import io.github.eyuppastirmaci.dioptra.cli.CliOptions
import io.github.eyuppastirmaci.dioptra.config.ConnectionResolution
import io.github.eyuppastirmaci.dioptra.config.ConnectionResolver
import io.github.eyuppastirmaci.dioptra.config.HoconConnectionProfileStore
import io.github.eyuppastirmaci.dioptra.config.HoconLastUsedConnectionStore
import io.github.eyuppastirmaci.dioptra.config.LastUsedConnectionMetadata
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisConnectionManager
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisHealthClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisInfoClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyBrowserClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyDetailClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisMemoryUsageMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTtlMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTypeMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.codec.Utf8ValueDecoder
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisInfoParser
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisKeyspaceParser
import io.github.eyuppastirmaci.dioptra.presentation.tui.TuiApplication
import io.github.eyuppastirmaci.dioptra.presentation.tui.error.UserFacingErrorMessage
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.ConnectionAttemptResult
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.ConnectionScreen
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.DashboardScreen
import io.github.eyuppastirmaci.dioptra.presentation.tui.screen.TuiScreen
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

        try {
            tuiApplication.run(initialScreenFor(resolution))
        } finally {
            activeConnectionManagers.forEach { it.close() }
            activeConnectionManagers.clear()
        }
    }

    private fun initialScreenFor(resolution: ConnectionResolution): TuiScreen {
        return when (resolution) {
            is ConnectionResolution.Ready -> {
                logger.info(
                    "Resolved Redis connection from {}: {}",
                    resolution.source,
                    resolution.config.maskedUri,
                )
                when (val attempt = connectToDashboard(resolution.config)) {
                    is ConnectionAttemptResult.Success -> attempt.nextScreen
                    is ConnectionAttemptResult.Failure -> connectionScreen(
                        initialConfig = resolution.config,
                        initialMessage = attempt.message,
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
                )
            }
        }
    }

    private fun connectionScreen(
        initialConfig: RedisConnectionConfig,
        initialMessage: String?,
    ): ConnectionScreen {
        return ConnectionScreen(
            profileStore = HoconConnectionProfileStore(),
            lastUsedConnectionStore = lastUsedConnectionStore,
            initialConfig = initialConfig,
            initialMessage = initialMessage,
            connect = ::connectToDashboard,
        )
    }

    private fun connectToDashboard(config: RedisConnectionConfig): ConnectionAttemptResult {
        var redisConnectionManager: RedisConnectionManager? = null

        return runCatching {
            val activeRedisConnectionManager = RedisConnectionManager(config)
            redisConnectionManager = activeRedisConnectionManager
            activeRedisConnectionManager.connect()
            activeConnectionManagers.add(activeRedisConnectionManager)
            saveLastUsedConnection(config)

            ConnectionAttemptResult.Success(
                nextScreen = createDashboardScreen(activeRedisConnectionManager),
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

    private fun createDashboardScreen(redisConnectionManager: RedisConnectionManager): DashboardScreen {
        val redisCommands = redisConnectionManager.syncCommands()
        val redisBinaryValueCommands = redisConnectionManager.syncBinaryValueCommands()

        val redisHealthClient = RedisHealthClient(redisCommands)
        val redisInfoClient = RedisInfoClient(redisCommands)
        val redisKeyBrowserClient = RedisKeyBrowserClient(redisCommands)
        val redisKeyDetailClient = RedisKeyDetailClient(redisBinaryValueCommands)

        val redisInfoParser = RedisInfoParser()
        val redisKeyspaceParser = RedisKeyspaceParser()

        val redisTypeMapper = RedisTypeMapper()
        val redisTtlMapper = RedisTtlMapper()
        val redisMemoryUsageMapper = RedisMemoryUsageMapper()
        val redisValueDecoder = Utf8ValueDecoder()

        val loadDashboardUseCase = LoadDashboardUseCase(
            connectionConfig = redisConnectionManager.config,
            redisHealthClient = redisHealthClient,
            redisInfoClient = redisInfoClient,
            redisInfoParser = redisInfoParser,
            redisKeyspaceParser = redisKeyspaceParser,
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

        return DashboardScreen(
            snapshot = loadDashboardUseCase.load(),
            browseKeysUseCase = browseKeysUseCase,
            loadKeyDetailUseCase = loadKeyDetailUseCase,
            disconnect = {
                redisConnectionManager.close()
                activeConnectionManagers.remove(redisConnectionManager)
                connectionScreen(
                    initialConfig = RedisConnectionConfig(),
                    initialMessage = "Disconnected.",
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
