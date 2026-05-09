package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.config.HoconConnectionProfileStore
import io.github.eyuppastirmaci.dioptra.config.NamespaceAnalysisSettings
import io.github.eyuppastirmaci.dioptra.config.RedisConnectionConfig

class SaveNamespaceAnalysisSettingsUseCase(
    private val profileStore: HoconConnectionProfileStore,
    private val connectionConfig: RedisConnectionConfig,
) {

    fun canPersist(): Boolean {
        return persistedProfile() != null
    }

    fun unavailableReason(): String {
        return "Persistent save unavailable for this session. Connect through a saved profile or save the connection first."
    }

    fun save(settings: NamespaceAnalysisSettings): NamespaceAnalysisSettings {
        val profile = persistedProfile()
            ?.copy(namespaceAnalysisSettings = settings)
            ?: throw IllegalStateException(unavailableReason())

        profileStore.saveProfile(profile)
        return settings
    }

    private fun persistedProfile() = connectionConfig.name
        .takeIf { it.isNotBlank() }
        ?.let(profileStore.load()::findProfile)
        ?.takeIf { profile ->
            profile.host == connectionConfig.host &&
                profile.port == connectionConfig.port &&
                profile.database == connectionConfig.database &&
                profile.username == connectionConfig.username &&
                profile.tls == connectionConfig.tls
        }
}