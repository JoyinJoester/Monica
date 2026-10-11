package takagi.ru.monica.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val VAULT_OVERVIEW_MAX_CARD_PINS = 200
internal const val VAULT_OVERVIEW_MAX_ITEM_PINS = 8

enum class VaultOverviewModule {
    CARDS, RECENT, ITEMS, FAVORITES, TYPES, FOLDERS, DATABASES, ARCHIVE, TRASH;

    companion object {
        val defaultOrder: List<String> = entries.map { it.name }
    }
}

/** Display preferences only; opening the overview never changes the saved list layout. */
@Serializable
data class VaultOverviewConfig(
    val order: List<String> = VaultOverviewModule.defaultOrder,
    val hidden: Set<String> = emptySet(),
    val collapsed: Set<String> = setOf(VaultOverviewModule.DATABASES.name),
    val pinnedCards: List<String> = emptyList(),
    val pinnedItems: List<String> = emptyList(),
    val recommendCards: Boolean = true,
    val recommendItems: Boolean = true,
    // Older settings omit the default scope. Include synced vaults when it is absent;
    // an explicitly saved local/database scope still survives decoding and round trips.
    val scope: String = "all",
    val excludedFrequentItems: Set<String> = emptySet(),
) {
    fun normalized(): VaultOverviewConfig {
        val itemPins = pinnedItems.filter(String::isNotBlank).distinct().take(VAULT_OVERVIEW_MAX_ITEM_PINS)
        return copy(
            order = (order + VaultOverviewModule.defaultOrder.filterNot { it == VaultOverviewModule.RECENT.name })
                .distinct().filter { it in VaultOverviewModule.defaultOrder }.toMutableList().apply {
                    if (VaultOverviewModule.RECENT.name !in this) {
                        add(indexOf(VaultOverviewModule.CARDS.name) + 1, VaultOverviewModule.RECENT.name)
                    }
                },
            hidden = hidden.intersect(VaultOverviewModule.defaultOrder.toSet()),
            collapsed = collapsed.intersect(VaultOverviewModule.defaultOrder.toSet()),
            pinnedCards = pinnedCards.filter(String::isNotBlank).distinct().take(VAULT_OVERVIEW_MAX_CARD_PINS),
            pinnedItems = itemPins,
            excludedFrequentItems = excludedFrequentItems.filterTo(linkedSetOf(), String::isNotBlank) - itemPins.toSet(),
            scope = scope.takeIf {
                it == "all" || it == "local" || (DATABASE_SCOPE.matches(it) && it.substringAfter(':').toLongOrNull() != null)
            } ?: "local",
        )
    }

    /** Removing a recommendation must survive later opens, syncs and app restarts. */
    fun removeFrequentItems(identities: Collection<String>): VaultOverviewConfig {
        val removed = identities.filterTo(hashSetOf(), String::isNotBlank)
        return copy(
            pinnedItems = pinnedItems.filterNot(removed::contains),
            excludedFrequentItems = excludedFrequentItems + removed,
        )
    }

    fun togglePinnedItem(identity: String): VaultOverviewConfig = when {
        identity.isBlank() -> this
        identity in pinnedItems -> copy(pinnedItems = pinnedItems - identity)
        pinnedItems.size >= VAULT_OVERVIEW_MAX_ITEM_PINS -> this
        else -> copy(pinnedItems = pinnedItems + identity, excludedFrequentItems = excludedFrequentItems - identity)
    }

    fun encode(): String = json.encodeToString(normalized())

    companion object {
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        private val DATABASE_SCOPE = Regex("(keepass|bitwarden|mdbx):[1-9][0-9]*")

        fun decode(value: String?): VaultOverviewConfig =
            value?.let { runCatching { json.decodeFromString<VaultOverviewConfig>(it).normalized() }.getOrNull() }
                ?: VaultOverviewConfig()
    }
}
