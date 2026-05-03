package io.github.eyuppastirmaci.dioptra.config

data class DioptraConfig(
    val defaultProfile: String? = null,
    val profiles: List<RedisConnectionProfile> = emptyList(),
) {

    fun findProfile(name: String): RedisConnectionProfile? {
        return profiles.firstOrNull { it.name == name }
    }
}
