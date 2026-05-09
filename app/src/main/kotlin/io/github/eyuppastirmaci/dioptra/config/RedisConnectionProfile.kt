package io.github.eyuppastirmaci.dioptra.config

data class RedisConnectionProfile(
    val name: String,
    val host: String = "localhost",
    val port: Int = 6379,
    val database: Int = 0,
    val username: String? = null,
    val tls: Boolean = false,
    val timeoutMillis: Long = 5_000,
    val requiresPassword: Boolean = false,
    val namespaceAnalysisSettings: NamespaceAnalysisSettings = NamespaceAnalysisSettings(),
    val riskAnalysisSettings: RiskAnalysisSettings = RiskAnalysisSettings(),
) {

    fun toConnectionConfig(password: String? = null): RedisConnectionConfig {
        return RedisConnectionConfig(
            name = name,
            host = host,
            port = port,
            database = database,
            username = username,
            password = password,
            tls = tls,
            timeoutMillis = timeoutMillis,
            namespaceAnalysisSettings = namespaceAnalysisSettings,
            riskAnalysisSettings = riskAnalysisSettings,
        )
    }
}
