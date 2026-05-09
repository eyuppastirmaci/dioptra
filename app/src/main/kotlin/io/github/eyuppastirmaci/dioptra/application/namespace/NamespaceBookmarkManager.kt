package io.github.eyuppastirmaci.dioptra.application.namespace

import io.github.eyuppastirmaci.dioptra.config.HoconNamespaceBookmarkStore

data class NamespaceBookmarkToggleResult(
    val bookmarks: Set<String>,
    val added: Boolean,
)

class NamespaceBookmarkManager(
    private val store: HoconNamespaceBookmarkStore,
) {

    fun load(profileName: String): Set<String> {
        return store.load(profileName)
    }

    fun toggle(
        profileName: String,
        namespaceName: String,
    ): NamespaceBookmarkToggleResult {
        val normalizedNamespace = namespaceName.trim()
        if (normalizedNamespace.isBlank()) {
            return NamespaceBookmarkToggleResult(
                bookmarks = load(profileName),
                added = false,
            )
        }

        val current = load(profileName)
        val added = normalizedNamespace !in current
        val next = if (added) {
            current + normalizedNamespace
        } else {
            current - normalizedNamespace
        }

        store.save(profileName, next)
        return NamespaceBookmarkToggleResult(
            bookmarks = next,
            added = added,
        )
    }

    fun remove(
        profileName: String,
        namespaceName: String,
    ): Set<String> {
        val next = load(profileName) - namespaceName
        store.save(profileName, next)
        return next
    }
}
