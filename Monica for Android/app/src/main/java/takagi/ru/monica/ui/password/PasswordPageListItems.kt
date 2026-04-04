package takagi.ru.monica.ui.password

import androidx.compose.ui.graphics.Color
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordPageContentType

internal sealed interface PasswordPageListItemUi {
    val key: String
}

internal data class PasswordPageCardItemUi(
    val key: String,
    val entry: PasswordEntry,
    val type: PasswordPageContentType,
    val badgeText: String? = null,
    val badgeColor: Color? = null,
    val passwordId: Long? = null,
    val secureItemId: Long? = null,
    val passkeyRecordId: Long? = null,
    val isDocument: Boolean = false
) {
    val supportsFavorite: Boolean = type != PasswordPageContentType.PASSKEY
    val sortTime: Long = entry.updatedAt.time
}

internal data class PasswordGroupListItemUi(
    val groupKey: String,
    val passwords: List<PasswordEntry>
) : PasswordPageListItemUi {
    override val key: String = "group:$groupKey"
}

internal data class PasswordManualStackGroupListItemUi(
    val groupKey: String,
    val cards: List<PasswordPageCardItemUi>
) : PasswordPageListItemUi {
    override val key: String = "manual_group:$groupKey"
}

internal data class PasswordSupplementaryListItemUi(
    val item: PasswordAggregateListItemUi
) : PasswordPageListItemUi {
    override val key: String = item.key
}

internal fun buildPasswordPageListItems(
    selectedContentTypes: Set<PasswordPageContentType>,
    groupedPasswords: Map<String, List<PasswordEntry>>,
    supplementaryItems: List<PasswordAggregateListItemUi>,
    manualStackGroups: List<PasswordManualStackGroupListItemUi> = emptyList()
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
    val mixedStackGroups = manualStackGroups
        .filter { it.cards.isNotEmpty() }
        .sortedByDescending { group -> group.cards.maxOf { it.sortTime } }

    return buildList {
        addAll(passwordItems)
        addAll(mixedStackGroups)
        addAll(aggregateCards)
    }
}

private fun buildPasswordGroupListItems(
    groupedPasswords: Map<String, List<PasswordEntry>>,
    includePasswords: Boolean
): List<PasswordGroupListItemUi> {
    if (!includePasswords) return emptyList()

    return groupedPasswords.entries
        .filter { (_, passwords) -> passwords.isNotEmpty() }
        .map { (groupKey, passwords) ->
        PasswordGroupListItemUi(
            groupKey = groupKey,
            passwords = passwords
        )
    }
}

internal fun PasswordEntry.toPasswordPageCardItemUi(): PasswordPageCardItemUi {
    return PasswordPageCardItemUi(
        key = passwordSelectionKey(id),
        entry = this,
        type = PasswordPageContentType.PASSWORD,
        passwordId = id
    )
}

internal fun PasswordAggregateListItemUi.toPasswordPageCardItemUi(): PasswordPageCardItemUi {
    return PasswordPageCardItemUi(
        key = key,
        entry = entry,
        type = type,
        badgeText = badgeText,
        badgeColor = badgeColor,
        secureItemId = secureItemId,
        passkeyRecordId = passkeyRecordId,
        isDocument = isDocument
    )
}

internal fun flattenPasswordPageCardItems(
    items: List<PasswordPageListItemUi>
): List<PasswordPageCardItemUi> {
    return buildList {
        items.forEach { item ->
            when (item) {
                is PasswordGroupListItemUi -> {
                    item.passwords.forEach { password ->
                        add(password.toPasswordPageCardItemUi())
                    }
                }

                is PasswordManualStackGroupListItemUi -> addAll(item.cards)

                is PasswordSupplementaryListItemUi -> add(item.item.toPasswordPageCardItemUi())
            }
        }
    }
}

internal fun resolveSelectedPasswordPageCardItems(
    items: List<PasswordPageListItemUi>,
    selectedKeys: Set<String>
): List<PasswordPageCardItemUi> {
    if (selectedKeys.isEmpty()) return emptyList()
    return flattenPasswordPageCardItems(items)
        .filter { card -> card.key in selectedKeys }
}

internal fun PasswordPageCardItemUi.toSelectedSupplementaryItemOrNull(): PasswordAggregateListItemUi? {
    if (type == PasswordPageContentType.PASSWORD) return null
    return PasswordAggregateListItemUi(
        key = key,
        entry = entry,
        type = type,
        badgeText = badgeText.orEmpty(),
        badgeColor = badgeColor ?: Color.Unspecified,
        sortTime = sortTime,
        secureItemId = secureItemId,
        passkeyRecordId = passkeyRecordId,
        isDocument = isDocument
    )
}
