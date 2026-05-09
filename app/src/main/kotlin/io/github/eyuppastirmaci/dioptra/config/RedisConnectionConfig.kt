package io.github.eyuppastirmaci.dioptra.config

data class RedisConnectionConfig(
    val name: String = "local",
    val host: String = "localhost",
    val port: Int = 6379,
    val database: Int = 0,
    val username: String? = null,
    val password: String? = null,
    val tls: Boolean = false,
    val timeoutMillis: Long = 5_000,
    val namespaceAnalysisSettings: NamespaceAnalysisSettings = NamespaceAnalysisSettings(),
    val riskAnalysisSettings: RiskAnalysisSettings = RiskAnalysisSettings(),
) {
    val uri: String
        get() = buildRedisUri(maskPassword = false)

    val maskedUri: String
        get() = buildRedisUri(maskPassword = true)

    override fun toString(): String {
        return "RedisConnectionConfig(name=$name, uri=$maskedUri, timeoutMillis=$timeoutMillis)"
    }

    private fun buildRedisUri(maskPassword: Boolean): String {
        val scheme = if (tls) "rediss" else "redis"
        val userInfo = buildUserInfo(maskPassword)

        return buildString {
            append(scheme)
            append("://")
            if (userInfo != null) {
                append(userInfo)
                append('@')
            }
            append(host)
            append(':')
            append(port)
            append('/')
            append(database)
        }
    }

    private fun buildUserInfo(maskPassword: Boolean): String? {
        val encodedUsername = username?.takeIf { it.isNotBlank() }?.let(CredentialMasker::encodeUriPart)
        val passwordValue = password

        return when {
            encodedUsername == null && passwordValue.isNullOrEmpty() -> null
            encodedUsername != null && passwordValue.isNullOrEmpty() -> encodedUsername
            encodedUsername == null -> ":${maskedOrEncodedPassword(passwordValue.orEmpty(), maskPassword)}"
            else -> "$encodedUsername:${maskedOrEncodedPassword(passwordValue.orEmpty(), maskPassword)}"
        }
    }

    private fun maskedOrEncodedPassword(
        value: String,
        maskPassword: Boolean,
    ): String {
        return if (maskPassword) {
            CredentialMasker.MASK
        } else {
            CredentialMasker.encodeUriPart(value)
        }
    }
}
