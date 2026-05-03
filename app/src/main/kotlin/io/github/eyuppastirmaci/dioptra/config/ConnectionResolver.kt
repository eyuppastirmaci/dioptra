package io.github.eyuppastirmaci.dioptra.config

import io.github.eyuppastirmaci.dioptra.cli.CliConnectionOptions
import java.net.URI

class ConnectionResolver(
    private val profileStore: HoconConnectionProfileStore = HoconConnectionProfileStore(),
) {

    fun resolve(options: CliConnectionOptions): ConnectionResolution {
        options.url?.takeIf { it.isNotBlank() }?.let { url ->
            return resolveUrl(url)
        }

        val appConfig = runCatching { profileStore.load() }.getOrElse { exception ->
            return ConnectionResolution.NeedsUserInput(
                reason = "Could not read Dioptra config: ${exception.message}",
            )
        }

        options.profile?.takeIf { it.isNotBlank() }?.let { profileName ->
            return resolveProfile(
                profileName = profileName,
                appConfig = appConfig,
                password = options.password,
                source = ConnectionSource.CliProfile,
            )
        }

        if (options.hasIndividualConnectionOptions()) {
            return ConnectionResolution.Ready(
                config = RedisConnectionConfig(
                    host = options.host ?: RedisConnectionConfig().host,
                    port = options.port ?: RedisConnectionConfig().port,
                    database = options.database ?: RedisConnectionConfig().database,
                    username = options.username,
                    password = options.password,
                    tls = options.tls,
                ),
                source = ConnectionSource.CliOptions,
            )
        }

        appConfig.defaultProfile?.takeIf { it.isNotBlank() }?.let { profileName ->
            return resolveProfile(
                profileName = profileName,
                appConfig = appConfig,
                password = options.password,
                source = ConnectionSource.DefaultProfile,
            )
        }

        return ConnectionResolution.NeedsUserInput(
            reason = "No connection source was provided.",
            partialConfig = RedisConnectionConfig(),
        )
    }

    private fun resolveUrl(url: String): ConnectionResolution {
        val uri = runCatching { URI(url) }.getOrElse {
            return ConnectionResolution.NeedsUserInput(
                reason = "Redis URL is invalid.",
            )
        }

        val scheme = uri.scheme
        if (scheme != "redis" && scheme != "rediss") {
            return ConnectionResolution.NeedsUserInput(
                reason = "Redis URL must use redis:// or rediss://.",
            )
        }

        val host = uri.host ?: return ConnectionResolution.NeedsUserInput(
            reason = "Redis URL must include a host.",
        )

        val userInfo = uri.rawUserInfo?.let(::parseUserInfo)

        return ConnectionResolution.Ready(
            config = RedisConnectionConfig(
                name = "cli-url",
                host = host,
                port = if (uri.port == -1) 6379 else uri.port,
                database = parseDatabase(uri.rawPath),
                username = userInfo?.username,
                password = userInfo?.password,
                tls = scheme == "rediss",
            ),
            source = ConnectionSource.CliUrl,
        )
    }

    private fun resolveProfile(
        profileName: String,
        appConfig: DioptraConfig,
        password: String?,
        source: ConnectionSource,
    ): ConnectionResolution {
        val profile = appConfig.findProfile(profileName)
            ?: return ConnectionResolution.NeedsUserInput(
                reason = "Connection profile '$profileName' was not found.",
            )

        val partialConfig = profile.toConnectionConfig(password = password)

        if (profile.requiresPassword && password.isNullOrEmpty()) {
            return ConnectionResolution.NeedsUserInput(
                reason = "Connection profile '$profileName' requires a password.",
                partialConfig = partialConfig,
            )
        }

        return ConnectionResolution.Ready(
            config = partialConfig,
            source = source,
        )
    }

    private fun CliConnectionOptions.hasIndividualConnectionOptions(): Boolean {
        return host != null ||
            port != null ||
            database != null ||
            username != null ||
            password != null ||
            passwordRequested ||
            tls
    }

    private fun parseDatabase(rawPath: String?): Int {
        val path = rawPath?.trim('/') ?: return 0
        return path.toIntOrNull() ?: 0
    }

    private fun parseUserInfo(rawUserInfo: String): UserInfo {
        val separatorIndex = rawUserInfo.indexOf(':')
        return if (separatorIndex == -1) {
            UserInfo(
                username = CredentialMasker.decodeUriPart(rawUserInfo),
                password = null,
            )
        } else {
            UserInfo(
                username = CredentialMasker.decodeUriPart(rawUserInfo.substring(0, separatorIndex)),
                password = CredentialMasker.decodeUriPart(rawUserInfo.substring(separatorIndex + 1)),
            )
        }
    }

    private data class UserInfo(
        val username: String?,
        val password: String?,
    )
}
