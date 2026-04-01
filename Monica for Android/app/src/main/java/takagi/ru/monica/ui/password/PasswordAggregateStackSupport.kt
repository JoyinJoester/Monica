package takagi.ru.monica.ui.password

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordPageAggregateStackEntry

internal data class PasswordAggregateManualStackBuildResult(
    val groups: List<PasswordManualStackGroupListItemUi>,
    val stackedItemKeys: Set<String>,
    val stackedPasswordIds: Set<Long>,
    val stackedAggregateKeys: Set<String>
)

internal fun buildPasswordAggregateManualStackGroups(
    stackEntries: List<PasswordPageAggregateStackEntry>,
    passwords: List<PasswordEntry>,
    aggregateItems: List<PasswordAggregateListItemUi>
): PasswordAggregateManualStackBuildResult {
    if (stackEntries.isEmpty()) {
        return PasswordAggregateManualStackBuildResult(
            groups = emptyList(),
            stackedItemKeys = emptySet(),
            stackedPasswordIds = emptySet(),
            stackedAggregateKeys = emptySet()
        )
    }

    val passwordCardsByKey = passwords.associate { password ->
        val card = password.toPasswordPageCardItemUi()
        card.key to card
    }
    val aggregateCardsByKey = aggregateItems.associate { item ->
        val card = item.toPasswordPageCardItemUi()
        card.key to card
    }
    val cardsByKey = passwordCardsByKey + aggregateCardsByKey

    val groups = stackEntries
        .groupBy(PasswordPageAggregateStackEntry::stackGroupId)
        .mapNotNull { (groupId, members) ->
            val cards = members
                .sortedBy(PasswordPageAggregateStackEntry::stackOrder)
                .mapNotNull { member -> cardsByKey[member.itemKey] }
            if (cards.size < 2 || cards.any { it.passwordId == null }) {
                null
            } else {
                PasswordManualStackGroupListItemUi(
                    groupKey = groupId,
                    cards = cards
                )
            }
        }
        .sortedByDescending { group -> group.cards.maxOf(PasswordPageCardItemUi::sortTime) }

    val stackedItemKeys = groups
        .flatMap { group -> group.cards.map(PasswordPageCardItemUi::key) }
        .toSet()
    val stackedPasswordIds = groups
        .flatMap { group -> group.cards.mapNotNull(PasswordPageCardItemUi::passwordId) }
        .toSet()
    val stackedAggregateKeys = stackedItemKeys - passwordCardsByKey.keys

    return PasswordAggregateManualStackBuildResult(
        groups = groups,
        stackedItemKeys = stackedItemKeys,
        stackedPasswordIds = stackedPasswordIds,
        stackedAggregateKeys = stackedAggregateKeys
    )
}
