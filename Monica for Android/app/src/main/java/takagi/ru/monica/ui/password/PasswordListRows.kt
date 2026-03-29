package takagi.ru.monica.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.ui.password.PasswordGroupListItemUi
import takagi.ru.monica.ui.password.PasswordListAggregateConfig
import takagi.ru.monica.ui.password.PasswordListCardBadge
import takagi.ru.monica.ui.password.PasswordPageListItemUi
import takagi.ru.monica.ui.password.PasswordListSingleCardItem
import takagi.ru.monica.ui.password.PasswordSupplementaryListItemUi
import takagi.ru.monica.ui.password.StackCardMode
import takagi.ru.monica.ui.password.StackedPasswordGroup
import takagi.ru.monica.ui.password.passwordSelectionKey
import takagi.ru.monica.ui.password.selectionKeysForPasswords
import takagi.ru.monica.viewmodel.PasswordViewModel

internal fun LazyListScope.passwordPageListRows(
    passwordPageListItems: List<PasswordPageListItemUi>,
    effectiveStackCardMode: StackCardMode,
    expandedGroups: Set<String>,
    itemToDelete: PasswordEntry?,
    onItemToDeleteChange: (PasswordEntry?) -> Unit,
    isSelectionMode: Boolean,
    onSelectionModeChange: (Boolean) -> Unit,
    selectedItemKeys: Set<String>,
    onSelectedItemKeysChange: (Set<String>) -> Unit,
    selectedPasswords: Set<Long>,
    showBatchDeleteDialog: Boolean,
    onShowBatchDeleteDialogChange: (Boolean) -> Unit,
    viewModel: PasswordViewModel,
    haptic: takagi.ru.monica.ui.haptic.HapticFeedbackHelper,
    onPasswordClick: (PasswordEntry) -> Unit,
    appSettings: AppSettings,
    coroutineScope: CoroutineScope,
    context: Context,
    passwordEntries: List<PasswordEntry>,
    aggregateConfig: PasswordListAggregateConfig?,
    aggregateUiState: PasswordListAggregateUiState
) {
    items(passwordPageListItems, key = { item -> item.key }) { listItem ->
        when (listItem) {
            is PasswordGroupListItemUi -> {
                val groupKey = listItem.groupKey
                val passwords = listItem.passwords
                val isExpanded = when (effectiveStackCardMode) {
                    StackCardMode.AUTO -> expandedGroups.contains(groupKey)
                    StackCardMode.ALWAYS_EXPANDED -> true
                }

                StackedPasswordGroup(
                    website = groupKey,
                    passwords = passwords,
                    isExpanded = isExpanded,
                    stackCardMode = effectiveStackCardMode,
                    enableSharedBounds = false,
                    swipedItemId = itemToDelete?.id,
                    onToggleExpand = {
                        if (effectiveStackCardMode == StackCardMode.AUTO) {
                            viewModel.toggleExpandedGroup(groupKey)
                        }
                    },
                    onPasswordClick = { password ->
                        if (isSelectionMode) {
                            val selectionKey = passwordSelectionKey(password.id)
                            onSelectedItemKeysChange(
                                if (selectionKey in selectedItemKeys) {
                                    selectedItemKeys - selectionKey
                                } else {
                                    selectedItemKeys + selectionKey
                                }
                            )
                        } else {
                            onPasswordClick(password)
                        }
                    },
                    onSwipeLeft = { password ->
                        if (itemToDelete == null) {
                            haptic.performWarning()
                            onItemToDeleteChange(password)
                        }
                    },
                    onSwipeRight = { password ->
                        haptic.performSuccess()
                        if (!isSelectionMode) {
                            onSelectionModeChange(true)
                        }
                        val selectionKey = passwordSelectionKey(password.id)
                        onSelectedItemKeysChange(
                            if (selectionKey in selectedItemKeys) {
                                selectedItemKeys - selectionKey
                            } else {
                                selectedItemKeys + selectionKey
                            }
                        )
                    },
                    onGroupSwipeRight = { groupPasswords ->
                        haptic.performSuccess()
                        if (!isSelectionMode) {
                            onSelectionModeChange(true)
                        }
                        val groupSelectionKeys = selectionKeysForPasswords(
                            groupPasswords.map(PasswordEntry::id)
                        )
                        val allSelected = groupSelectionKeys.all { it in selectedItemKeys }
                        onSelectedItemKeysChange(
                            if (allSelected) {
                                selectedItemKeys - groupSelectionKeys
                            } else {
                                selectedItemKeys + groupSelectionKeys
                            }
                        )
                    },
                    onToggleFavorite = { password ->
                        viewModel.toggleFavorite(password.id, !password.isFavorite)
                    },
                    onToggleGroupFavorite = {
                        coroutineScope.launch {
                            val allFavorited = passwords.all { it.isFavorite }
                            val newState = !allFavorited
                            passwords.forEach { password ->
                                viewModel.toggleFavorite(password.id, newState)
                            }
                            val message = if (newState) {
                                context.getString(R.string.group_favorited, passwords.size)
                            } else {
                                context.getString(R.string.group_unfavorited, passwords.size)
                            }
                            Toast.makeText(
                                context,
                                message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onToggleGroupCover = { password ->
                        coroutineScope.launch {
                            val websiteKey = password.website.ifBlank {
                                context.getString(R.string.filter_uncategorized)
                            }
                            val newCoverState = !password.isGroupCover
                            if (newCoverState) {
                                val currentIndex = passwords.indexOfFirst { it.id == password.id }
                                if (currentIndex > 0) {
                                    val reordered = passwords.toMutableList()
                                    val movedPassword = reordered.removeAt(currentIndex)
                                    reordered.add(0, movedPassword)
                                    val firstItemInGroup = passwordEntries.firstOrNull {
                                        it.website.ifBlank {
                                            context.getString(R.string.filter_uncategorized)
                                        } == websiteKey
                                    } ?: return@launch
                                    val startSortOrder = passwordEntries.indexOf(firstItemInGroup)
                                    viewModel.updateSortOrders(
                                        reordered.mapIndexed { idx, entry ->
                                            entry.id to (startSortOrder + idx)
                                        }
                                    )
                                }
                            }
                            viewModel.toggleGroupCover(password.id, websiteKey, newCoverState)
                        }
                    },
                    isSelectionMode = isSelectionMode,
                    selectedPasswords = selectedPasswords,
                    onToggleSelection = { id ->
                        val selectionKey = passwordSelectionKey(id)
                        onSelectedItemKeysChange(
                            if (selectionKey in selectedItemKeys) {
                                selectedItemKeys - selectionKey
                            } else {
                                selectedItemKeys + selectionKey
                            }
                        )
                    },
                    onOpenMultiPasswordDialog = { passwords ->
                        onPasswordClick(passwords.first())
                    },
                    onLongClick = { password ->
                        haptic.performLongPress()
                        if (!isSelectionMode) {
                            onSelectionModeChange(true)
                            onSelectedItemKeysChange(setOf(passwordSelectionKey(password.id)))
                        }
                    },
                    iconCardsEnabled = appSettings.iconCardsEnabled && appSettings.passwordPageIconEnabled,
                    unmatchedIconHandlingStrategy = appSettings.unmatchedIconHandlingStrategy,
                    passwordCardDisplayMode = appSettings.passwordCardDisplayMode,
                    passwordCardDisplayFields = appSettings.passwordCardDisplayFields,
                    showAuthenticator = appSettings.passwordCardShowAuthenticator,
                    hideOtherContentWhenAuthenticator = appSettings.passwordCardHideOtherContentWhenAuthenticator,
                    totpTimeOffsetSeconds = appSettings.totpTimeOffset,
                    smoothAuthenticatorProgress = appSettings.validatorSmoothProgress
                )
            }

            is PasswordSupplementaryListItemUi -> {
                val item = listItem.item
                PasswordListSingleCardItem(
                    entry = item.entry,
                    onClick = {
                        if (isSelectionMode) {
                            onSelectedItemKeysChange(
                                if (item.key in selectedItemKeys) {
                                    selectedItemKeys - item.key
                                } else {
                                    selectedItemKeys + item.key
                                }
                            )
                        } else {
                            when (item.type) {
                                PasswordPageContentType.AUTHENTICATOR ->
                                    item.secureItemId?.let { aggregateConfig?.onOpenTotp?.invoke(it) }

                                PasswordPageContentType.CARD_WALLET ->
                                    item.secureItemId?.let { itemId ->
                                        if (item.isDocument) {
                                            aggregateConfig?.onOpenDocument?.invoke(itemId)
                                        } else {
                                            aggregateConfig?.onOpenBankCard?.invoke(itemId)
                                        }
                                    }

                                PasswordPageContentType.NOTE ->
                                    aggregateConfig?.onOpenNote?.invoke(item.secureItemId)

                                PasswordPageContentType.PASSKEY ->
                                    item.passkeyCredentialId?.let {
                                        aggregateConfig?.onOpenPasskey?.invoke(it)
                                    }

                                PasswordPageContentType.PASSWORD -> Unit
                            }
                        }
                    },
                    onLongClick = {
                        haptic.performLongPress()
                        if (!isSelectionMode) {
                            onSelectionModeChange(true)
                            onSelectedItemKeysChange(setOf(item.key))
                        }
                    },
                    onSwipeLeft = {
                        haptic.performWarning()
                        if (!isSelectionMode) {
                            onSelectionModeChange(true)
                        }
                        onSelectedItemKeysChange(setOf(item.key))
                        if (!showBatchDeleteDialog) {
                            onShowBatchDeleteDialogChange(true)
                        }
                    },
                    onSwipeRight = {
                        haptic.performSuccess()
                        if (!isSelectionMode) {
                            onSelectionModeChange(true)
                        }
                        onSelectedItemKeysChange(
                            if (item.key in selectedItemKeys) {
                                selectedItemKeys - item.key
                            } else {
                                selectedItemKeys + item.key
                            }
                        )
                    },
                    isSwiped = false,
                    isSelectionMode = isSelectionMode,
                    isSelected = item.key in selectedItemKeys,
                    onToggleFavorite = when (item.type) {
                        PasswordPageContentType.AUTHENTICATOR -> {
                            {
                                item.secureItemId?.let {
                                    aggregateUiState.totpViewModel?.toggleFavorite(it, !item.entry.isFavorite)
                                }
                            }
                        }

                        PasswordPageContentType.CARD_WALLET -> {
                            {
                                item.secureItemId?.let { id ->
                                    if (item.isDocument) {
                                        aggregateUiState.documentViewModel?.toggleFavorite(id)
                                    } else {
                                        aggregateUiState.bankCardViewModel?.toggleFavorite(id)
                                    }
                                }
                            }
                        }

                        PasswordPageContentType.NOTE -> {
                            {
                                item.secureItemId?.let { noteId ->
                                    aggregateUiState.notes.firstOrNull { it.id == noteId }?.let { note ->
                                        val decoded = NoteContentCodec.decodeFromItem(note)
                                        aggregateUiState.noteViewModel?.updateNote(
                                            id = note.id,
                                            content = decoded.content,
                                            title = note.title,
                                            tags = decoded.tags,
                                            isMarkdown = decoded.isMarkdown,
                                            isFavorite = !note.isFavorite,
                                            createdAt = note.createdAt,
                                            categoryId = note.categoryId,
                                            imagePaths = note.imagePaths,
                                            keepassDatabaseId = note.keepassDatabaseId,
                                            keepassGroupPath = note.keepassGroupPath,
                                            bitwardenVaultId = note.bitwardenVaultId,
                                            bitwardenFolderId = note.bitwardenFolderId
                                        )
                                    }
                                }
                            }
                        }

                        PasswordPageContentType.PASSKEY,
                        PasswordPageContentType.PASSWORD -> null
                    },
                    unmatchedIconHandlingStrategy = aggregateUiState.cardStyle.unmatchedIconHandlingStrategy,
                    passwordCardDisplayMode = aggregateUiState.cardStyle.passwordCardDisplayMode,
                    passwordCardDisplayFields = aggregateUiState.cardStyle.passwordCardDisplayFields,
                    showAuthenticator = aggregateUiState.cardStyle.showAuthenticator,
                    hideOtherContentWhenAuthenticator = aggregateUiState.cardStyle.hideOtherContentWhenAuthenticator,
                    totpTimeOffsetSeconds = aggregateUiState.cardStyle.totpTimeOffsetSeconds,
                    smoothAuthenticatorProgress = aggregateUiState.cardStyle.smoothAuthenticatorProgress,
                    iconCardsEnabled = aggregateUiState.cardStyle.iconCardsEnabled,
                    enableSharedBounds = false,
                    badge = PasswordListCardBadge(
                        text = item.badgeText,
                        color = item.badgeColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}