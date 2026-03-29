package takagi.ru.monica.ui.password

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordPageContentType

internal sealed interface PasswordPageListItemUi {
    val key: String
}

internal data class PasswordGroupListItemUi(
    val groupKey: String,
    val passwords: List<PasswordEntry>
) : PasswordPageListItemUi {
    override val key: String = "group:$groupKey"
}

internal data class PasswordSupplementaryListItemUi(
    val item: PasswordAggregateListItemUi
) : PasswordPageListItemUi {
    override val key: String = item.key
}

internal fun buildPasswordPageListItems(
    selectedContentTypes: Set<PasswordPageContentType>,
    groupedPasswords: Map<String, List<PasswordEntry>>,
    supplementaryItems: List<PasswordAggregateListItemUi>
): List<PasswordPageListItemUi> {
    if (selectedContentTypes.isEmpty()) return emptyList()

    val passwordItems = buildPasswordGroupListItems(
        groupedPasswords = groupedPasswords,
        includePasswords =
            PasswordPageContentType.PASSWORD in selectedContentTypes ||
                PasswordPageContentType.AUTHENTICATOR in selectedContentTypes ||
                PasswordPageContentType.PASSKEY in selectedContentTypes
    )
    val aggregateCards = supplementaryItems.map { item ->
        PasswordSupplementaryListItemUi(item = item)
    }

    if (aggregateCards.isEmpty()) return passwordItems
    if (passwordItems.isEmpty()) return aggregateCards

    return passwordItems + aggregateCards
}

private fun buildPasswordGroupListItems(
    groupedPasswords: Map<String, List<PasswordEntry>>,
    includePasswords: Boolean
): List<PasswordGroupListItemUi> {
    if (!includePasswords) return emptyList()

    return groupedPasswords.entries.map { (groupKey, passwords) ->
        PasswordGroupListItemUi(
            groupKey = groupKey,
            passwords = passwords
        )
    }
}
