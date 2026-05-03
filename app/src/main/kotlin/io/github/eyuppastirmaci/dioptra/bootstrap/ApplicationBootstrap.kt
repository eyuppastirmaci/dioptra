package io.github.eyuppastirmaci.dioptra.bootstrap

import io.github.eyuppastirmaci.dioptra.application.dashboard.LoadDashboardUseCase
import io.github.eyuppastirmaci.dioptra.application.key.BrowseKeysUseCase
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.LettuceRedisClientFactory
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisHealthClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisInfoClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.RedisKeyBrowserClient
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisMemoryUsageMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTtlMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.mapper.RedisTypeMapper
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisInfoParser
import io.github.eyuppastirmaci.dioptra.infrastructure.redis.parser.RedisKeyspaceParser
import io.github.eyuppastirmaci.dioptra.presentation.tui.TuiApplication
import io.github.eyuppastirmaci.dioptra.presentation.tui.terminal.TerminalFactoryProvider
import io.lettuce.core.api.StatefulRedisConnection

class ApplicationBootstrap {

    fun start() {
        val redisConnectionConfig = RedisConnectionConfig()
        val redisClientFactory = LettuceRedisClientFactory(redisConnectionConfig)

        var redisConnection: StatefulRedisConnection<String, String>? = null

        try {
            val activeRedisConnection = redisClientFactory.connect()
            redisConnection = activeRedisConnection

            val redisCommands = redisClientFactory.syncCommands(activeRedisConnection)

            val redisHealthClient = RedisHealthClient(redisCommands)
            val redisInfoClient = RedisInfoClient(redisCommands)
            val redisKeyBrowserClient = RedisKeyBrowserClient(redisCommands)

            val redisInfoParser = RedisInfoParser()
            val redisKeyspaceParser = RedisKeyspaceParser()

            val redisTypeMapper = RedisTypeMapper()
            val redisTtlMapper = RedisTtlMapper()
            val redisMemoryUsageMapper = RedisMemoryUsageMapper()

            val loadDashboardUseCase = LoadDashboardUseCase(
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

            val terminalFactory = TerminalFactoryProvider.create()
            val tuiApplication = TuiApplication(terminalFactory)

            val application = DioptraApplication(
                loadDashboardUseCase = loadDashboardUseCase,
                browseKeysUseCase = browseKeysUseCase,
                tuiApplication = tuiApplication,
            )

            application.run()
        } finally {
            redisConnection?.close()
            redisClientFactory.close()
        }
    }
}